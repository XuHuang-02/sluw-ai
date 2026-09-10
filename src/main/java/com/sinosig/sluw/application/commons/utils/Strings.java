package com.sinosig.sluw.application.commons.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Strings {

    private static final Logger logger = LoggerFactory.getLogger(Strings.class);

    private Strings() {

    }

    public static String upper(String str) {
        if (str == null) return null;
        return str.toUpperCase();
    }

    public static String firstCapital(String word) {
        if (word == null) return null;
        if (word.length() > 1) {
            return word.substring(0, 1).toUpperCase() + word.substring(1);
        }
        return word.toUpperCase();
    }

    public static String lower(String str) {
        if (str == null) return null;
        return str.toLowerCase();
    }

    public static String nvl(String... args) {
        if (args == null) return "";
        for (int i = 0; i < args.length; i++) {
            if (args[i] != null && !"".equals(args[i].trim())) {
                return args[i].trim();
            }
        }
        return "";
    }

    /**
     * String --> Byte
     */
    public static Byte toByte(String s) {
        Byte t = 0;
        try {
            if (s == null) {
                return null;
            }
            s = s.trim();
            if (s.startsWith("+")) {
                s = s.substring(1);
            }
            t = Byte.parseByte(s);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return t;
    }

    /**
     * String --> Byte Default
     */
    public static Byte toByte(String s, Byte defaultValue) {
        Byte t = 0;
        try {
            if (s == null) {
                return null;
            }
            s = s.trim();
            if (s.startsWith("+")) {
                s = s.substring(1);
            }
            t = Byte.parseByte(s);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return defaultValue;
        }
        return t;
    }

    /**
     * String --> Short
     */
    public static Short toShort(String s) {
        Short t = 0;
        try {
            if (s == null) {
                return null;
            }
            s = s.trim();
            if (s.startsWith("+")) {
                s = s.substring(1);
            }
            t = Short.parseShort(s);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return t;
    }

    /**
     * String --> Short Default
     */
    public static Short toShort(String s, Short defaultValue) {
        Short t = 0;
        try {
            if (s == null) {
                return null;
            }
            s = s.trim();
            if (s.startsWith("+")) {
                s = s.substring(1);
            }
            t = Short.parseShort(s);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return defaultValue;
        }
        return t;
    }

    /**
     * String --> Character
     */
    public static Character toCharacter(String s) {
        Character t = 0;
        try {
            if (s == null) {
                return null;
            }
            t = s.charAt(0);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return t;
    }

    /**
     * String --> Character defaultValue
     */
    public static Character toCharacter(String s, Character defaultValue) {
        Character t = 0;
        try {
            if (s == null) {
                return null;
            }
            t = s.charAt(0);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return defaultValue;
        }
        return t;
    }

    /**
     * String --> Integer
     */
    public static Integer toInteger(String s) {
        Integer t = 0;
        try {
            if (s == null) {
                return null;
            }
            s = s.trim();
            if (s.startsWith("+")) {
                s = s.substring(1);
            }
            t = Integer.parseInt(s);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return t;
    }

    /**
     * String --> Integer
     */
    public static Integer toInteger(String s, Integer defaultValue) {
        Integer t = defaultValue;
        try {
            if (s == null) {
                return null;
            }
            s = s.trim();
            if (s.startsWith("+")) {
                s = s.substring(1);
            }
            t = Integer.parseInt(s);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return defaultValue;
        }
        return t;
    }

    /**
     * String --> Long
     */
    public static Long toLong(String s) {
        Long t = 0L;
        try {
            if (s == null) {
                return null;
            }
            s = s.trim();
            if (s.startsWith("+")) {
                s = s.substring(1);
            }
            t = Long.parseLong(s);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return t;
    }

    /**
     * String --> Long
     */
    public static Long toLong(String s, Long defaultValue) {
        Long t = 0L;
        try {
            if (s == null) {
                return null;
            }
            s = s.trim();
            if (s.startsWith("+")) {
                s = s.substring(1);
            }
            t = Long.parseLong(s);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return defaultValue;
        }
        return t;
    }

    /**
     * String --> Float
     */
    public static Float toFloat(String s) {
        Float t = 0f;
        try {
            if (s == null) {
                return null;
            }
            s = s.trim();
            t = Float.parseFloat(s);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return t;
    }

    /**
     * String --> Float
     */
    public static Float toFloat(String s, Float defaultValue) {
        Float t = 0f;
        try {
            if (s == null) {
                return null;
            }
            s = s.trim();
            t = Float.parseFloat(s);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return defaultValue;
        }
        return t;
    }

    /**
     * String --> Double
     */
    public static Double toDouble(String s) {
        Double d = 0d;
        try {
            if (s == null || "".equals(s)) {
                return null;
            }
            s = s.trim();
            d = Double.parseDouble(s);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        if (d.isNaN() || d.isInfinite()) {
            System.err.println("parse double number error input : " + s);
            return 0d;
        }
        return d;
    }

    public static Double toDouble(String s, Double defaultValue) {
        Double d = defaultValue;
        try {
            if (s == null) {
                return null;
            }
            s = s.trim();
            d = Double.parseDouble(s);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        if (d != null && (d.isNaN() || d.isInfinite())) {
            logger.error("parse double number error input : " + s);
            return defaultValue;
        }
        return d;
    }

    /**
     * String --> Boolean
     */
    public static Boolean toBoolean(String b) {
        return "y".equalsIgnoreCase(b) ||
                "yes".equalsIgnoreCase(b) ||
                "on".equalsIgnoreCase(b) ||
                "true".equalsIgnoreCase(b) ||
                "t".equalsIgnoreCase(b) ||
                "1".equals(b);
    }

    /**
     * String --> java.util.Date
     */
    public static Date toDate(String s) {
        if (s == null || s.trim().length() < 8) return null;
        s = s.trim();
        Date t = null;
        try {
            if (s.length() == 8) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                t = sdf.parse(s);
            } else if (s.length() == 10 && s.indexOf("-") != -1) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                t = sdf.parse(s);
            } else if (s.length() == 10 && s.indexOf("/") != -1) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
                t = sdf.parse(s);
            } else if (s.length() == 15) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd HHmmss");
                t = sdf.parse(s);
            } else if (s.length() == 19 && s.indexOf("-") != -1) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                t = sdf.parse(s);
            } else if (s.length() == 19 && s.indexOf("/") != -1) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
                t = sdf.parse(s);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return t;
    }

    public static Date toDate(String s, String format) {
        if (s == null || s.trim().length() < 8) return null;
        s = s.trim();
        Date t = null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            t = sdf.parse(s);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return t;
    }

    public static Date toDates(String s, String defaultDate) {
        if (s == null || s.trim().length() < 8) return null;
        s = s.trim();
        Date t = null;
        try {
            if (s.length() == 8) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                t = sdf.parse(s);
            } else if (s.length() == 10 && s.indexOf("-") != -1) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
                t = sdf.parse(s);
            } else if (s.length() == 10 && s.indexOf("/") != -1) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
                t = sdf.parse(s);
            }
        } catch (Exception e) {
            //logger.debug(e.getMessage(), e);
            return toDate(defaultDate);
        }
        return t;
    }

    /**
     * String --> java.sql.Date
     */
    public static java.sql.Date toSQLDate(String s) {
        Date d = toDate(s);
        java.sql.Date t = null;
        if (d != null) {
            t = new java.sql.Date(d.getTime());
        }
        return t;
    }


    public static boolean isEmpty(String s) {
        return s == null || s.trim().equals("");
    }

    public static boolean isNoEmpty(String s) {
        return !isEmpty(s);
    }

    public static String format(int value, String format) {
        return new DecimalFormat(format).format(value);
    }

    public static String format(long value, String format) {
        return new DecimalFormat(format).format(value);
    }

    public static String format(Double value, String format) {
        return new DecimalFormat(format).format(value);
    }

    public static String format(Float value, String format) {
        return new DecimalFormat(format).format(value);
    }

    public static String format(Date value, String format) {
        return new SimpleDateFormat(format).format(value);
    }

    public static String format(java.sql.Date value, String format) {
        return new SimpleDateFormat(format).format(value);
    }

    public static String format(String dates, String format) {
        Date date = toDate(dates);
        if (date == null) {
            return "";
        }
        return new SimpleDateFormat(format).format(date);
    }

    public static int length(String value) {
        if (value == null)
            return 0;
        return value.trim().getBytes().length;
    }

    public static int lengthGBK(String value) {
        if (value == null)
            return 0;
        try {
            return value.trim().getBytes("GBK").length;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }

    public static int lengthUTF8(String value) {
        if (value == null)
            return 0;
        try {
            return value.trim().getBytes("UTF-8").length;
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }

    public static String substring(String string, int start, int end) {
        if (string == null) return "";
        try {
            return string.substring(start, end);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return "";
    }

    public static String substringAvailable(String string, int length) {
        if (string == null || length == 0) return "";
        if (string.length() <= length) {
            return string;
        } else {
            return string.substring(0, length);
        }
    }

    /**
     * 返回布尔值：string like 'prefix%'
     *
     * @param string
     * @param prefix
     * @return
     */
    public static boolean likeBf(String string, String prefix) {
        if (string == null || prefix == null) {
            return false;
        }
        return string.startsWith(prefix);
    }

    /**
     * 返回布尔值：string like '%suffix'
     *
     * @param string
     * @param suffix
     * @return
     */
    public static boolean likeAf(String string, String suffix) {
        if (string == null || suffix == null) {
            return false;
        }
        return string.endsWith(suffix);
    }

    /**
     * 转义双引号 '"' --> \"
     *
     * @param str
     * @return
     */
    public static String escapeDoubleQuote(String str) {
        if (str != null) {
            return str.replaceAll("(?=\")", "\\\\");
        }
        return null;
    }

    public static String join(String... keys) {
        return join(keys, ",");
    }

    public static String concat(String... strings) {
        return join(strings, "");
    }

    public static String join(String[] keys, String splitment) {
        if (isEmpty(splitment)) {
            splitment = "";
        }
        if (keys == null) {
            return "";
        }
        StringBuffer ret = new StringBuffer("");
        for (int i = 0; i < keys.length; i++) {
            if (i > 0) {
                ret.append(splitment);
            }
            ret.append(keys[i]);
        }
        return ret.toString();
    }

    public static String join(String[][] keys) {
        if (keys != null) {
            StringBuffer sb = new StringBuffer("");
            sb.append("[");
            for (int i = 0; i < keys.length; i++) {
                sb.append("[");
                sb.append(join(keys[i]));
                sb.append("]\n");
            }
            sb.append("]");
            return sb.toString();
        }
        return "";
    }

    public static String[] split(String string, String splitment) {
        if (isEmpty(string)) {
            return null;
        }
        if (isEmpty(splitment)) {
            return new String[]{string};
        }
        String[] ret = null;
        if (string != null) {
            ret = string.split(splitment);
            for (int i = 0; i < ret.length; i++) {
                if ("null".equals(ret[i])) {
                    ret[i] = null;
                }
            }
        }
        return ret;
    }

    /**
     * 将字符串分割成数组,但是去掉空数据
     *
     * @param string
     * @param splitment
     * @return
     */
    public static String[] splitNoEmpty(String string, String splitment) {
        if (isEmpty(string)) {
            return null;
        }
        if (isEmpty(splitment)) {
            return new String[]{string};
        }
        ArrayList<String> retTemp = new ArrayList<String>();
        String[] ret = null;
        if (string != null) {
            ret = string.split(splitment);
            if (ret != null)
                for (int i = 0; i < ret.length; i++) {
                    if (!isEmpty(ret[i])) {
                        retTemp.add(ret[i].trim());
                    }
                }
            if (retTemp.size() > 0) {
                ret = new String[retTemp.size()];
                for (int i = 0; i < ret.length; i++) {
                    ret[i] = retTemp.get(i);
                }
            } else {
                ret = null;
            }
        }
        return ret;
    }

    /**
     * 去两端双引号
     */
    public static String unQuote(String string) {
        if (isEmpty(string)) {
            return "";
        }
        return string.trim().replaceAll("^[\"]*|[\"]*$", "");
    }

    /**
     * 字符串左补位
     *
     * @param orginString - 原字符串
     * @param odd         - 补位字符串
     * @param num         - 总长度
     * @return
     */
    public static String lpad(String orginString, String odd, int num) {
        int length = orginString.length();
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < num - length; i++) {
            sb.append(odd);
        }
        return sb.append(orginString).toString();
    }

    /**
     * 字符串右补位
     *
     * @param orginString - 原字符串
     * @param odd         - 补位字符串
     * @param num         - 总长度
     * @return
     */
    public static String rpad(String orginString, String odd, int num) {
        int length = orginString.length();
        StringBuffer sb = new StringBuffer(orginString);
        for (int i = 0; i < num - length; i++) {
            sb.append(odd);
        }
        return sb.toString();
    }

    public static String trim(String string) {
        if (string != null) {
            return string.trim();
        }
        return "";
    }

    public static String precut(String string, String substr) {
        int pos = string.indexOf(substr);
        if (pos != -1) {
            return string.substring(0, pos);
        }
        return "";
    }

    public static String postcut(String string, String substr) {
        int pos = string.lastIndexOf(substr);
        if (pos != -1 && substr.length() + pos < string.length() - 1) {
            return string.substring(substr.length() + pos + 1);
        }
        return "";
    }

    public static boolean isNULLs(String string) {
        return string != null && string.trim().equals("null");
    }

    /**
     * 补位函数
     * （左补位）
     *
     * @param str - 原始字符串
     * @param len - 总长度
     * @param pad - 补位字符串
     * @return
     */
    public static String lpad(String str, int len, String pad) {
        if (str != null) {
            int len1 = len - str.length();
            StringBuffer sb = new StringBuffer();
            if (len1 > 0) {
                for (int i = 0; i < len1; i++) {
                    sb.append(pad);
                }
            }
            return sb.append(str).toString();
        }
        return null;
    }

    /**
     * 格式化16进制
     *
     * @param src
     * @param len
     * @return
     */
    public static String formatHex(int src, int len) {
        String hexString = Integer.toHexString(src);
        return lpad(hexString, len, "0");
    }

    public static String postfix(String str) {
        return postfix(str, null);
    }

    public static String postfix(String str, String point) {
        if (str == null) {
            return "";
        }
        if (point == null) {
            point = ".";
        }
        int pointPos = str.lastIndexOf(point);
        if (pointPos < 0 || pointPos >= str.length() - 1) {
            return "";
        }
        return str.substring(pointPos + 1);
    }

    public static String shield(String str, String replacement, String regex) {
        if (isEmpty(str) || isEmpty(regex)) {
            return str;
        }
        if (isEmpty(replacement)) {
            replacement = "*";
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(str);
        if (matcher.find()) {
            String groups = matcher.group(1);
            String repStr = "";
            if (groups.length() > 0) {
                repStr = lpad("", groups.length(), replacement);
                str = str.replace(groups, repStr);
            }
        }
        return str;
    }

    public static Date toDateWithFormat(String s, String format) {
        if (s == null || s.trim().length() < 8) return null;
        s = s.trim();
        Date t = null;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            t = sdf.parse(s);
        } catch (Exception e) {
            //logger.debug(e.getMessage(), e);
        }
        return t;
    }


    public static byte[] getUTF8BytesFromGBKString(String gbkStr) {
        int n = gbkStr.length();
        byte[] utfBytes = new byte[3 * n];
        int k = 0;
        for (int i = 0; i < n; i++) {
            int m = gbkStr.charAt(i);
            if (m < 128 && m >= 0) {
                utfBytes[k++] = (byte) m;
                continue;
            }
            utfBytes[k++] = (byte) (0xe0 | (m >> 12));
            utfBytes[k++] = (byte) (0x80 | ((m >> 6) & 0x3f));
            utfBytes[k++] = (byte) (0x80 | (m & 0x3f));
        }
        if (k < utfBytes.length) {
            byte[] tmp = new byte[k];
            System.arraycopy(utfBytes, 0, tmp, 0, k);
            return tmp;
        }
        return utfBytes;
    }


    /**
     * 获得定长的字符串
     *
     *
     */
    public static String getEnoughLengthStr(String str, int length, String type) {
        if ("0".equals(type)) {
            for (int i = str.length(); i < length; i++) {
                str = type + str;
            }
        } else if (" ".equals(type)) {
            for (int i = str.length(); i < length; i++) {
                str = str + type;
            }
        }
        return str;
    }
}
