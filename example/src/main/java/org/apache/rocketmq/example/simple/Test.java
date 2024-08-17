package org.apache.rocketmq.example.simple;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * @author qiang.lin
 * @since 2024/4/17
 */
public class Test {
    public static void main(String[] args) {
        String s = "grey_virtual-phone-async-abnormal-number-degrade-call-record-consumer_virtual-phone-async";
        System.out.println(s.startsWith("grey_") ? s.substring(5, s.lastIndexOf("_")) : s);
        Date yyyyMMdd = getDateByStr("20240412", "yyyyMMdd");
        System.out.println(yyyyMMdd.getTime());
    }
    public static Date getDateByStr(String input, String format) {
        SimpleDateFormat formatter = new SimpleDateFormat(format);
        Date date;
        try {
            date = formatter.parse(input);
        } catch (Exception e) {
            throw new IllegalArgumentException("字符格式错误，转换时间失败");
        }
        return date;
    }
}
