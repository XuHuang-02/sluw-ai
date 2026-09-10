package com.sinosig.sluw.application.commons.log;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 敏感信息处理
public class SensitiveDataConverter extends MessageConverter {

    // 暂时不启用 8.13
   @Override
    public String convert(ILoggingEvent event) {
        // 原始日志
        String oriLogMsg = event.getFormattedMessage();
        // 脱敏后日志
        return invokeMsg(oriLogMsg);
    }

    private static String converterCanRun = "true";// 日志脱敏开关

    private static String sensitiveDataKeys = "idno,name";// 日志脱敏关键字

    /**
     * 处理日志字符串，返回脱敏后字符串
     * @param oriMsg
     * @return
     */
    public String invokeMsg(final String oriMsg) {
        String tempMsg = oriMsg;
        if ("true".equals(converterCanRun)) {
            if (sensitiveDataKeys != null && sensitiveDataKeys.length() > 0) {
                String[] keysArray = sensitiveDataKeys.split(",");
                for (String key : keysArray) {
                    int index = -1;
                    do {
                        index = tempMsg.indexOf(key, index + 1);// 返回tempMsg中key第一次出现的索引
                        if (index != -1) {
                            // 判断key是否为单词字符
                            if (isWordChar(tempMsg, key, index)) {
                                continue;
                            }
                            // 寻找值的开始位置
                            int valueStart = getValueStartIndex(tempMsg, index + key.length());

                            // 查找值的结束位置（逗号，分号）
                            int valueEnd = getValueEndIndex(tempMsg, valueStart);
                            // 对获取的值进行脱敏
                            String subStr = tempMsg.substring(valueStart, valueEnd);
                            subStr = awayFromSensitive(subStr, key);
                            tempMsg = tempMsg.substring(0, valueStart) + subStr + tempMsg.substring(valueEnd);
                        }
                    } while (index != -1);
                }
            }
        }
        return tempMsg;
    }


    private static Pattern pattern = Pattern.compile("[0-9a-zA-Z]");

    // 判断从字符串msg获取的key值是否为单词 ， index为key在msg中的索引值
    private boolean isWordChar(String msg, String key, int index) {
        if (index != 0) {
            char preCh = msg.charAt(index - 1);
            Matcher match = pattern.matcher(preCh + "");
            if (match.matches()) {
                return true;
            }
        }
        // 判断key后面一个字符
        char nextCh = msg.charAt(index + key.length());
        Matcher match = pattern.matcher(nextCh + "");
        if (match.matches()) {
            return true;
        }
        return false;
    }

    /**
     * 获取value值的开始位置
     * @param msg 要查找的字符串
     * @param valueStart 查找的开始位置
     * @return
     */
    private int getValueStartIndex(String msg, int valueStart) {
        do {
            char ch = msg.charAt(valueStart);
            if (ch == ':') {// key与value的分隔符(json格式下)
                valueStart ++;
                ch = msg.charAt(valueStart);
                if (ch == '"') {
                    valueStart ++;
                }
                break;// 找到值开始的位置
            } else {
                valueStart ++;
            }
        } while (true);
        return valueStart;
    }
    /**
     * 获取value值的结束位置
     * @param msg
     * @param valueEnd
     * @return
     */
    private int getValueEndIndex(String msg, int valueEnd) {
        do {
            if (valueEnd == msg.length()) {
                break;
            }
            char ch = msg.charAt(valueEnd);
            if (ch == '"') {// 引号时，判断下一个值是结束，逗号决定是否为值的结束
                if (valueEnd + 1 == msg.length()) {
                    break;
                }
                char nextCh = msg.charAt(valueEnd + 1);
                if (nextCh == ',') {
                    while (valueEnd > 0) {
                        char preCh = msg.charAt(valueEnd - 1);
                        if (preCh != '\\') {
                            break;
                        }
                        valueEnd --;
                    }
                    break;
                } else {
                    valueEnd ++;
                }
            } else if (ch == ',' || ch == '}') {
                break;
            } else {
                valueEnd ++;
            }
        } while (true);
        return valueEnd;
    }

    private String awayFromSensitive(String subMsg, String key) {
        // idNo：身份证号 name：姓名 应和关键字保持一致
        if ("idno".equals(key)) {
            return SensitiveInfoUtils.idCardNum(subMsg);
        }
        if ("name".equals(key)) {
            return SensitiveInfoUtils.chineseName(subMsg);
        }
        return "";
    }


}
