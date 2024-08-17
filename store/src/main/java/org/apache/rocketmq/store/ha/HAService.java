/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.store.ha;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.rocketmq.common.ServiceThread;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.remoting.common.RemotingUtil;
import org.apache.rocketmq.store.CommitLog;
import org.apache.rocketmq.store.DefaultMessageStore;

/**
 * k1 主从同步核心实现类
 */
public class HAService {
    private static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.STORE_LOGGER_NAME);

    private final AtomicInteger connectionCount = new AtomicInteger(0);

    private final List<HAConnection> connectionList = new LinkedList<>();

    private final AcceptSocketService acceptSocketService;

    private final DefaultMessageStore defaultMessageStore;

    private final WaitNotifyObject waitNotifyObject = new WaitNotifyObject();
    private final AtomicLong push2SlaveMaxOffset = new AtomicLong(0);

    private final GroupTransferService groupTransferService;

    private final HAClient haClient;

    public HAService(final DefaultMessageStore defaultMessageStore) throws IOException {
        this.defaultMessageStore = defaultMessageStore;
        this.acceptSocketService =
            new AcceptSocketService(defaultMessageStore.getMessageStoreConfig().getHaListenPort());
        this.groupTransferService = new GroupTransferService();
        this.haClient = new HAClient();
    }

    public void updateMasterAddress(final String newAddr) {
        if (this.haClient != null) {
            this.haClient.updateMasterAddress(newAddr);
        }
    }

    public void putRequest(final CommitLog.GroupCommitRequest request) {
        this.groupTransferService.putRequest(request);
    }

    public boolean isSlaveOK(final long masterPutWhere) {
        boolean result = this.connectionCount.get() > 0;
        result =
            result
                && ((masterPutWhere - this.push2SlaveMaxOffset.get()) < this.defaultMessageStore
                .getMessageStoreConfig().getHaSlaveFallbehindMax());
        return result;
    }

    /**
     *  k2 master在收到slave的拉取请求后被调用，表示从slave当前已同步偏移量，
     *   既然收到slave的反馈信息，需要唤醒某些producer的发送线程
     *   如果slave收到的确认偏移量大于push2SlaveMaxOffset，则更新push2SlaveMaxOffset，然后唤醒groupTransferService线程
     *   各producer发送线程在次判断自己本次发送的信息是否已经成功复制到slave
     * @param offset
     */
    public void notifyTransferSome(final long offset) {
        // offset = 101
        for (long value = this.push2SlaveMaxOffset.get(); offset > value; ) {
            // value = 100
            boolean ok = this.push2SlaveMaxOffset.compareAndSet(value, offset);
            if (ok) {
                // true
                this.groupTransferService.notifyTransferSome();
                break;
            } else {
                value = this.push2SlaveMaxOffset.get();
            }
        }
    }

    public AtomicInteger getConnectionCount() {
        return connectionCount;
    }

    // public void notifyTransferSome() {
    // this.groupTransferService.notifyTransferSome();
    // }

    /**
     * K2 HA 启动
     *  1、主broker启动，并在特定端口上监听从broker的连接
     *  2、从broker主动连接主broker，主broker接收客户端连接，并建立tcp连接
     *  3、从broker主动向主broker发送待拉取消息偏移量，主broker解析请求并返回消息给从broker
     *  4、从broker保存并继续发送新的消息同步请求
     * @throws Exception
     */
    public void start() throws Exception {
        this.acceptSocketService.beginAccept();
        this.acceptSocketService.start();
        this.groupTransferService.start();
        this.haClient.start();
    }

    public void addConnection(final HAConnection conn) {
        synchronized (this.connectionList) {
            this.connectionList.add(conn);
        }
    }

    public void removeConnection(final HAConnection conn) {
        synchronized (this.connectionList) {
            this.connectionList.remove(conn);
        }
    }

    public void shutdown() {
        this.haClient.shutdown();
        this.acceptSocketService.shutdown(true);
        this.destroyConnections();
        this.groupTransferService.shutdown();
    }

    public void destroyConnections() {
        synchronized (this.connectionList) {
            for (HAConnection c : this.connectionList) {
                c.shutdown();
            }

            this.connectionList.clear();
        }
    }

    public DefaultMessageStore getDefaultMessageStore() {
        return defaultMessageStore;
    }

    public WaitNotifyObject getWaitNotifyObject() {
        return waitNotifyObject;
    }

    public AtomicLong getPush2SlaveMaxOffset() {
        return push2SlaveMaxOffset;
    }

    /**
     * k1 HA master端监听客户端连接实现类
     *  Listens to slave connections to create {@link HAConnection}.
     */
    class AcceptSocketService extends ServiceThread {
        // k3 broker服务监听套接字（IP+port 10912）
        private final SocketAddress socketAddressListen;
        // k3 服务端Socket，基于NIO
        private ServerSocketChannel serverSocketChannel;
        // k3 选择器，基于NIO
        private Selector selector;

        public AcceptSocketService(final int port) {
            this.socketAddressListen = new InetSocketAddress(port);
        }

        /**
         * k2 Starts listening to slave connections.
         *  创建serverSocketChannel、创建selector、设置TCP ReuseAddress
         *  绑定监听端口、设置非阻塞模式、注册OP_ACCEPT连接事件
         *
         * @throws Exception If fails.
         */
        public void beginAccept() throws Exception {
            // 创建serverSocketChannel
            this.serverSocketChannel = ServerSocketChannel.open();
            // 创建selector
            this.selector = RemotingUtil.openSelector();
            // 设置TCP ReuseAddress
            this.serverSocketChannel.socket().setReuseAddress(true);
            // 绑定监听端口
            this.serverSocketChannel.socket().bind(this.socketAddressListen);
            // 设置非阻塞模式
            this.serverSocketChannel.configureBlocking(false);
            // 注册OP_ACCEPT连接事件
            this.serverSocketChannel.register(this.selector, SelectionKey.OP_ACCEPT);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void shutdown(final boolean interrupt) {
            super.shutdown(interrupt);
            try {
                this.serverSocketChannel.close();
                this.selector.close();
            } catch (IOException e) {
                log.error("AcceptSocketService shutdown exception", e);
            }
        }

        /**
         * k2 标准的基于NIO的服务端实例
         *  selector每1s处理一次连接就绪事件，
         *  连接事件就绪后，调用ServerSocketChannel#accept()创建SocketChannel，
         *  然后为每一个连接创建一个HAConnection对象，该对象负责M-S的数据同步
         * {@inheritDoc}
         */
        @Override
        public void run() {
            log.info(this.getServiceName() + " service started");

            while (!this.isStopped()) {
                try {
                    this.selector.select(1000);
                    Set<SelectionKey> selected = this.selector.selectedKeys();

                    if (selected != null) {
                        for (SelectionKey k : selected) {
                            if ((k.readyOps() & SelectionKey.OP_ACCEPT) != 0) {
                                SocketChannel sc = ((ServerSocketChannel) k.channel()).accept();

                                if (sc != null) {
                                    HAService.log.info("HAService receive new connection, "
                                        + sc.socket().getRemoteSocketAddress());

                                    try {
                                        HAConnection conn = new HAConnection(HAService.this, sc);
                                        conn.start();
                                        HAService.this.addConnection(conn);
                                    } catch (Exception e) {
                                        log.error("new HAConnection exception", e);
                                        sc.close();
                                    }
                                }
                            } else {
                                log.warn("Unexpected ops in select " + k.readyOps());
                            }
                        }

                        selected.clear();
                    }
                } catch (Exception e) {
                    log.error(this.getServiceName() + " service has exception.", e);
                }
            }

            log.info(this.getServiceName() + " service end");
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String getServiceName() {
            return AcceptSocketService.class.getSimpleName();
        }
    }

    /**
     * k1 主从同步通知实现类
     *  当主从同步复制结束（数据传输到slave，无需关注slave落盘）后，通知由于等待HA同步结果而阻塞的producer线程
     *  GroupTransferService Service
     */
    class GroupTransferService extends ServiceThread {

        private final WaitNotifyObject notifyTransferObject = new WaitNotifyObject();
        private volatile List<CommitLog.GroupCommitRequest> requestsWrite = new ArrayList<>();
        private volatile List<CommitLog.GroupCommitRequest> requestsRead = new ArrayList<>();

        public synchronized void putRequest(final CommitLog.GroupCommitRequest request) {
            synchronized (this.requestsWrite) {
                this.requestsWrite.add(request);
            }
            if (hasNotified.compareAndSet(false, true)) {
                waitPoint.countDown(); // notify
            }
        }

        public void notifyTransferSome() {
            this.notifyTransferObject.wakeup();
        }

        private void swapRequests() {
            List<CommitLog.GroupCommitRequest> tmp = this.requestsWrite;
            this.requestsWrite = this.requestsRead;
            this.requestsRead = tmp;
        }

        /**
         * k2 负责当主从同步复制结束后通知由于等待HA同步结果而阻塞的producer线程
         *  判断主从是否同步完成的依据是：slave中已成功复制的最大偏移量是否 >= producer发送消息后broker返回下一条消息的起始偏移量
         *  是：代表主从同步复制已完成，唤醒producer发送线程
         *  否：等待1s再判断，每一个任务在一批任务中循环判断5次
         */
        private void doWaitTransfer() {
            synchronized (this.requestsRead) {
                if (!this.requestsRead.isEmpty()) {
                    for (CommitLog.GroupCommitRequest req : this.requestsRead) {
                        boolean transferOK = HAService.this.push2SlaveMaxOffset.get() >= req.getNextOffset();
                        for (int i = 0; !transferOK && i < 5; i++) {
                            this.notifyTransferObject.waitForRunning(1000);
                            transferOK = HAService.this.push2SlaveMaxOffset.get() >= req.getNextOffset();
                            // false
                        }

                        if (!transferOK) {
                            // false
                            log.warn("transfer messsage to slave timeout, " + req.getNextOffset());
                        }

                        req.wakeupCustomer(transferOK);
                    }

                    this.requestsRead.clear();
                }
            }
        }

        public void run() {
            log.info(this.getServiceName() + " service started");

            while (!this.isStopped()) {
                try {
                    this.waitForRunning(10);
                    this.doWaitTransfer();
                } catch (Exception e) {
                    log.warn(this.getServiceName() + " service has exception. ", e);
                }
            }

            log.info(this.getServiceName() + " service end");
        }

        @Override
        protected void onWaitEnd() {
            this.swapRequests();
        }

        @Override
        public String getServiceName() {
            return GroupTransferService.class.getSimpleName();
        }
    }

    /**
     * K1 HA Slave端实现类
     */
    class HAClient extends ServiceThread {
        // k3 socket读缓存区大小4M
        private static final int READ_MAX_BUFFER_SIZE = 1024 * 1024 * 4;
        // k3 master地址
        private final AtomicReference<String> masterAddress = new AtomicReference<>();
        // k3 slave向master发起主从同步的拉起偏移量
        private final ByteBuffer reportOffset = ByteBuffer.allocate(8);
        // k3 网络传输通道
        private SocketChannel socketChannel;
        // k3 NIO事件选择器
        private Selector selector;
        // k3 上一次写入时间戳
        private long lastWriteTimestamp = System.currentTimeMillis();
        // k3 反馈slave当前复制进度，commitLog文件最大偏移量
        private long currentReportedOffset = 0;
        // k3 本次已处理读缓存区的指针
        private int dispatchPostion = 0;
        // k3 读缓存区 4M
        private ByteBuffer byteBufferRead = ByteBuffer.allocate(READ_MAX_BUFFER_SIZE);
        // k3 读缓存区备份，与ByteBuffer进行交换
        private ByteBuffer byteBufferBackup = ByteBuffer.allocate(READ_MAX_BUFFER_SIZE);

        public HAClient() throws IOException {
            this.selector = RemotingUtil.openSelector();
        }

        public void updateMasterAddress(final String newAddr) {
            String currentAddr = this.masterAddress.get();
            if (currentAddr == null || !currentAddr.equals(newAddr)) {
                this.masterAddress.set(newAddr);
                log.info("update master address, OLD: " + currentAddr + " NEW: " + newAddr);
            }
        }

        private boolean isTimeToReportOffset() {
            long interval =
                HAService.this.defaultMessageStore.getSystemClock().now() - this.lastWriteTimestamp;
            boolean needHeart = interval > HAService.this.defaultMessageStore.getMessageStoreConfig()
                .getHaSendHeartbeatInterval();

            return needHeart;
        }

        /**
         * k2 基于NIO的网络写
         *  先将ByteBuffer的position设为0，limit设为待写入字节长度，然后调用putLong将待拉取偏移量写入ByteBuffer中，
         *  需要将ByteBuffer从写模式切换到读模式，手动将position设置为0，limit设置为可读长度（也可以直接调用ByteBuffer.flip()）
         *  特别留意：调用网络通道的write方法是在一个while循环中反复判断byteBuffer是否全部写入通道中，这是由于NIO是一个非阻塞IO，
         *  调用一次write方法不一定会将ByteBuffer可读字节全部写入
         * @param maxOffset
         * @return
         */
        private boolean reportSlaveMaxOffset(final long maxOffset) {
            this.reportOffset.position(0);
            this.reportOffset.limit(8);
            this.reportOffset.putLong(maxOffset);
            this.reportOffset.position(0);
            this.reportOffset.limit(8);

            for (int i = 0; i < 3 && this.reportOffset.hasRemaining(); i++) {
                try {
                    this.socketChannel.write(this.reportOffset);
                } catch (IOException e) {
                    log.error(this.getServiceName()
                        + "reportSlaveMaxOffset this.socketChannel.write exception", e);
                    return false;
                }
            }

            return !this.reportOffset.hasRemaining();
        }

        private void reallocateByteBuffer() {
            int remain = READ_MAX_BUFFER_SIZE - this.dispatchPostion;
            if (remain > 0) {
                this.byteBufferRead.position(this.dispatchPostion);

                this.byteBufferBackup.position(0);
                this.byteBufferBackup.limit(READ_MAX_BUFFER_SIZE);
                this.byteBufferBackup.put(this.byteBufferRead);
            }

            this.swapByteBuffer();

            this.byteBufferRead.position(remain);
            this.byteBufferRead.limit(READ_MAX_BUFFER_SIZE);
            this.dispatchPostion = 0;
        }

        private void swapByteBuffer() {
            ByteBuffer tmp = this.byteBufferRead;
            this.byteBufferRead = this.byteBufferBackup;
            this.byteBufferBackup = tmp;
        }

        /**
         * k2 处理网络读的nio示例
         *  循环判断byteBufferRead是否还有剩余空间
         *  是：则调用socketChannel.read()，将通道中的数据读入到读缓存区
         *   如果读取到的字节数大于0，重置读取到0字节的次数，并更新最后一次写入时间戳（lastWriteTimestamp），
         *   然后调用dispatchReadRequest()将读取到的所有消息全部追加到消息内存映射文件中，然后再次反馈拉取进度给服务器
         *   如果连续三次从网络通道中读取到0字节，则结束本次读，返回true
         *   如果读取到的字节数小于0或发送异常，则返回false
         * @return
         */
        private boolean processReadEvent() {
            int readSizeZeroTimes = 0;
            while (this.byteBufferRead.hasRemaining()) {
                try {
                    int readSize = this.socketChannel.read(this.byteBufferRead);
                    if (readSize > 0) {
                        lastWriteTimestamp = HAService.this.defaultMessageStore.getSystemClock().now();
                        readSizeZeroTimes = 0;
                        boolean result = this.dispatchReadRequest();
                        if (!result) {
                            log.error("HAClient, dispatchReadRequest error");
                            return false;
                        }
                    } else if (readSize == 0) {
                        if (++readSizeZeroTimes >= 3) {
                            break;
                        }
                    } else {
                        log.info("HAClient, processReadEvent read socket < 0");
                        return false;
                    }
                } catch (IOException e) {
                    log.info("HAClient, processReadEvent read socket exception", e);
                    return false;
                }
            }

            return true;
        }

        private boolean dispatchReadRequest() {
            final int msgHeaderSize = 8 + 4; // phyoffset + size
            int readSocketPos = this.byteBufferRead.position();

            while (true) {
                int diff = this.byteBufferRead.position() - this.dispatchPostion;
                if (diff >= msgHeaderSize) {
                    long masterPhyOffset = this.byteBufferRead.getLong(this.dispatchPostion);
                    int bodySize = this.byteBufferRead.getInt(this.dispatchPostion + 8);

                    long slavePhyOffset = HAService.this.defaultMessageStore.getMaxPhyOffset();

                    if (slavePhyOffset != 0) {
                        if (slavePhyOffset != masterPhyOffset) {
                            log.error("master pushed offset not equal the max phy offset in slave, SLAVE: "
                                + slavePhyOffset + " MASTER: " + masterPhyOffset);
                            return false;
                        }
                    }

                    if (diff >= (msgHeaderSize + bodySize)) {
                        byte[] bodyData = new byte[bodySize];
                        this.byteBufferRead.position(this.dispatchPostion + msgHeaderSize);
                        this.byteBufferRead.get(bodyData);

                        HAService.this.defaultMessageStore.appendToCommitLog(masterPhyOffset, bodyData);

                        this.byteBufferRead.position(readSocketPos);
                        this.dispatchPostion += msgHeaderSize + bodySize;

                        if (!reportSlaveMaxOffsetPlus()) {
                            return false;
                        }

                        continue;
                    }
                }

                if (!this.byteBufferRead.hasRemaining()) {
                    this.reallocateByteBuffer();
                }

                break;
            }

            return true;
        }

        private boolean reportSlaveMaxOffsetPlus() {
            boolean result = true;
            long currentPhyOffset = HAService.this.defaultMessageStore.getMaxPhyOffset();
            if (currentPhyOffset > this.currentReportedOffset) {
                this.currentReportedOffset = currentPhyOffset;
                result = this.reportSlaveMaxOffset(this.currentReportedOffset);
                if (!result) {
                    this.closeMaster();
                    log.error("HAClient, reportSlaveMaxOffset error, " + this.currentReportedOffset);
                }
            }

            return result;
        }

        /**
         * k2 slave 连接 master
         *
         * @return
         * @throws ClosedChannelException
         */
        private boolean connectMaster() throws ClosedChannelException {
            // 如果socketChannel为空，则尝试连接master
            if (null == socketChannel) {
                String addr = this.masterAddress.get();
                // 如果master地址不为空，则建立到master的TCP连接
                if (addr != null) {

                    SocketAddress socketAddress = RemotingUtil.string2SocketAddress(addr);
                    if (socketAddress != null) {
                        this.socketChannel = RemotingUtil.connect(socketAddress);
                        if (this.socketChannel != null) {
                            // 注册OP_READ网络读事件，
                            this.socketChannel.register(this.selector, SelectionKey.OP_READ);
                        }
                    }
                }
                // 初始化currentReportedOffset为commitlog文件的最大偏移量
                this.currentReportedOffset = HAService.this.defaultMessageStore.getMaxPhyOffset();
                // lastWriteTimestamp为当前时间戳
                this.lastWriteTimestamp = System.currentTimeMillis();
            }

            return this.socketChannel != null;
        }

        private void closeMaster() {
            if (null != this.socketChannel) {
                try {

                    SelectionKey sk = this.socketChannel.keyFor(this.selector);
                    if (sk != null) {
                        sk.cancel();
                    }

                    this.socketChannel.close();

                    this.socketChannel = null;
                } catch (IOException e) {
                    log.warn("closeMaster exception. ", e);
                }

                this.lastWriteTimestamp = 0;
                this.dispatchPostion = 0;

                this.byteBufferBackup.position(0);
                this.byteBufferBackup.limit(READ_MAX_BUFFER_SIZE);

                this.byteBufferRead.position(0);
                this.byteBufferRead.limit(READ_MAX_BUFFER_SIZE);
            }
        }

        @Override
        public void run() {
            log.info(this.getServiceName() + " service started");

            while (!this.isStopped()) {
                try {
                    // k3 1、slave连接master，如果socketChannel为空，则尝试连接Master
                    //  broker角色、master地址在配置文件，返回是否成功连接上broker
                    if (this.connectMaster()) {

                        // k3 2、判断是否需要向mater反馈当前待拉取偏移量，master与slave的ha心跳发送间隔默认为5s
                        if (this.isTimeToReportOffset()) {
                            /*
                              k3 3、向master反馈拉取偏移量
                                  对slave端：发送下次待拉取的消息偏移量，
                                  对master：既可以认为是slave本次请求拉取的消息偏移量，也可以认为是slave的消息同步ack确认信息
                             */
                            boolean result = this.reportSlaveMaxOffset(this.currentReportedOffset);
                            if (!result) {
                                this.closeMaster();
                            }
                        }

                        // k3 4、时间选择，执行间隔1s
                        this.selector.select(1000);

                        // k3 5、处理网络请求，即slave处理从master传回的消息数据
                        boolean ok = this.processReadEvent();
                        if (!ok) {
                            this.closeMaster();
                        }

                        if (!reportSlaveMaxOffsetPlus()) {
                            continue;
                        }

                        long interval =
                            HAService.this.getDefaultMessageStore().getSystemClock().now()
                                - this.lastWriteTimestamp;
                        if (interval > HAService.this.getDefaultMessageStore().getMessageStoreConfig()
                            .getHaHousekeepingInterval()) {
                            log.warn("HAClient, housekeeping, found this connection[" + this.masterAddress
                                + "] expired, " + interval);
                            this.closeMaster();
                            log.warn("HAClient, master not response some time, so close connection");
                        }
                    } else {
                        this.waitForRunning(1000 * 5);
                    }
                } catch (Exception e) {
                    log.warn(this.getServiceName() + " service has exception. ", e);
                    this.waitForRunning(1000 * 5);
                }
            }

            log.info(this.getServiceName() + " service end");
        }
        // private void disableWriteFlag() {
        // if (this.socketChannel != null) {
        // SelectionKey sk = this.socketChannel.keyFor(this.selector);
        // if (sk != null) {
        // int ops = sk.interestOps();
        // ops &= ~SelectionKey.OP_WRITE;
        // sk.interestOps(ops);
        // }
        // }
        // }
        // private void enableWriteFlag() {
        // if (this.socketChannel != null) {
        // SelectionKey sk = this.socketChannel.keyFor(this.selector);
        // if (sk != null) {
        // int ops = sk.interestOps();
        // ops |= SelectionKey.OP_WRITE;
        // sk.interestOps(ops);
        // }
        // }
        // }

        @Override
        public String getServiceName() {
            return HAClient.class.getSimpleName();
        }
    }
}
