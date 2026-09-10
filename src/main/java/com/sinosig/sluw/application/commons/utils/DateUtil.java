package com.sinosig.sluw.application.commons.utils;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * for *Entity
 * 
 * @author Guosubin
 *
 */
public class DateUtil {

	private DateUtil(){
    	
    }
	public static Date date(){
		return Strings.toDate(fdate());
	}
	
	public static String fdate(){
		return fdate(new Date());
	}
	/**
	 * 日期格式为：yyyyMMdd
	 * 
	 * */
	public static String f8date() {
		return Strings.format(new Date(), "yyyyMMdd");
	}
	
	public static String fdate(Date date){
		return Strings.format(date, "yyyy-MM-dd");
	}
	public static String fullDate() {
		return fullDate(new Date());
	}
	public static String fullDate(Date date) {
		return Strings.format(date, "yyyy-MM-dd HH:mm:ss");
	}
	public static String ftime(){
		return Strings.format(new Date(), "HH:mm:ss");
	}
	/**
	 * 返回指定日期的偏移后的日期
	 * 
	 * @param date org date
	 * @param day int --> day
	 * @param month_year int[] --> month, year
	 * @return
	 */
	public static Date date(Date date, int day, int...month_year) {
		if(date == null) {
			return null;
		}
		GregorianCalendar gcr = new GregorianCalendar();
		gcr.setTime(date);
		if(month_year!= null){
			int year = Arrays.get(month_year, 1);
			int month = Arrays.get(month_year, 0);
			if(year != 0){
				gcr.add(Calendar.YEAR, year);
			}
			if(month != 0) {
				gcr.add(Calendar.MONTH, month);
			}
		}
		if(day != 0){
			gcr.add(Calendar.DATE, day);
		}
		return gcr.getTime();
	}
	/**
	 * 返回指定日期的偏移后的日期
	 * 
	 * @param sdate org date
	 * @param day int --> day
	 * @param month_year int[] --> month, year
	 * @return
	 */
	public static String fdate(String sdate, int day, int...month_year) {
		if(sdate == null) {
			return null;
		}
		GregorianCalendar gcr = new GregorianCalendar();
		gcr.setTime(Strings.toDate(sdate));
		if(month_year!= null){
			int year = Arrays.get(month_year, 1);
			int month = Arrays.get(month_year, 0);
			if(year != 0){
				gcr.add(Calendar.YEAR, year);
			}
			if(month != 0) {
				gcr.add(Calendar.MONTH, month);
			}
		}
		if(day != 0){
			gcr.add(Calendar.DATE, day);
		}
		return fdate(gcr.getTime());
	}

	/**
	 * 日期比较
	 * 
	 * @param d1 - 日期1
	 * @param d2 - 日期2
	 * @return 0  - d1 == d2 or d1 == d2 == null
	 * 		  -2  - d1 == null
	 * 		  -1  - d1 < d2
	 * 		   1  - d1 > d2
	 * 	 	   2  - d2 == null
	 */
	public static int compareDate(Date d1, Date d2) {
		if(d1 == null && d2 == null){
			return 0;
		}
		if(d1 == null && d2 != null){
			return -2;
		}
		if(d1 != null && d2 == null){
			return 2;
		}
		if(d2.getTime() > d1.getTime()){
			return -1;
		}
		if(d1.getTime() > d2.getTime()){
			return 1;
		}
		return 0;
	}
	
	public static int compareDate(String date1, String date2) {
		Date d1 = Strings.toDate(date1);
		Date d2 = Strings.toDate(date2);
		return compareDate(d1, d2);
	}
	/** String --> java.util.Date */
	public static Date parseDate(String s) {
		if(s == null || s.trim().length() < 8) {
			return null;
		}
		s = s.trim();
		Date t = null;
		try {
			if(s.length() == 8){
				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
				t = sdf.parse(s);
			} else if (s.length() == 10 && s.indexOf("-") != -1){
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
				t = sdf.parse(s);
			} else if (s.length() == 10 && s.indexOf("/") != -1){
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd");
				t = sdf.parse(s);
			}
		} catch (Exception e) {
			//logger.debug(e.getMessage(), e);
		}
		return t;
	}
	
	public static String format(Date value) {
		return new SimpleDateFormat("yyyy-MM-dd").format(value);
	}
	
	public static String format(Date value, String pFormat) {
		return new SimpleDateFormat(pFormat).format(value);
	}
	
	public static String format(Calendar mCalendar, String pFormat) {
		return new SimpleDateFormat(pFormat).format(mCalendar.getTime());
	}
	
	public static String format(String value) {
		  try {
			Date date = new SimpleDateFormat("yyyy-MM-dd").parse(value);
			return format(date);
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		  return null;
	}
	
	/**
	 * 返回两个 日期之间的所有日期 
	 * @param startStr
	 * @param endStr
	 * @return
	 */
	public static String[] twoDate(String startStr,String endStr){
		Date start = DateUtil.parseDate(startStr);
		Date end = DateUtil.parseDate(endStr);
		List<String> result = new ArrayList<String>();
		Calendar tempStart = Calendar.getInstance();
		tempStart.setTime(start);
		tempStart.add(Calendar.DAY_OF_YEAR, 0);
		Calendar tempEnd = Calendar.getInstance();
		tempEnd.setTime(end);
		tempEnd.add(Calendar.DAY_OF_YEAR, 1);
		while (tempStart.before(tempEnd)){
			result.add(DateUtil.format(tempStart.getTime()));
			tempStart.add(Calendar.DAY_OF_YEAR, 1);
		}
		int dateSize = result.size();
		String[] cc = new String[dateSize];
		cc = result.toArray(cc);
		return cc;
	}
	
	
    public static int betweenDays(String date1, String date2) {
    	Date d1 = Strings.toDate(date1);
    	Date d2 = Strings.toDate(date2);
    	if(d1 != null && d2 != null){
    		return (int) (Math.abs(d1.getTime() - d2.getTime()) / (1000*3600*24));
    	}
        return 0;
    }

    public static String GMTDate(String format, String timeZone){
		SimpleDateFormat sdf = new SimpleDateFormat(format, new Locale("en", "US"));
		sdf.setTimeZone(TimeZone.getTimeZone(timeZone));
		String date = sdf.format(new Date());
		return date;
	}

    public static String fullTime() {
        DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmssSSS");
        return dateFormat.format(new Date());
    }

	public static String fullTime2() {
		DateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
		return dateFormat.format(new Date());
	}

	public static String httpDate(){
		return GMTDate("EEE, d MMM yyyy HH:mm:ss z", "GMT");
	}

    public static String getGMTDate() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(new Date());
    }

	/**
	 * 获得一个月最后一天
	 * @param year int
	 * @param month int
	 * @return String
	 */
	public static String getLastDay(int year,int month)
	{
		if(month==1||month==3||month==5||month==7||month==8||month==10||month==12)
		{
			if(month<10){
				return year+"-0"+month+"-31";
			}
			return year+"-"+month+"-31";
		}else if(month==4||month==6||month==9||month==11)
		{
			if(month<10){
				return year+"-0"+month+"-30";
			}
			return year+"-"+month+"-30";
		}else
		{
			if(year%400==0||(year%4==0&&year%100!=0))
			{
				if(month<10){
					return year+"-0"+month+"-29";
				}
				return year+"-"+month+"-29";
			}else
			{
				if(month<10){
					return year+"-0"+month+"-28";
				}
				return year+"-"+month+"-28";
			}
		}
	}

	/**
	 * 日期格式转换：将(例如:20050817)转化为(例如:2005-08-17)
	 *
	 * @param strDate
	 *            (日期字符串，格式:yyyyMMdd)
	 * @return String (日期字符串，格式:yyyy-MM-dd)
	 */
	public static String tranOutDate(String strDate) {
		String tString = null;

		if ( strDate == null ||  strDate.trim().equals("")) {
			return null;
		}
		String Date = strDate.trim();
		if (Date != "" && Date != null) {
			if (Date.length() == 8) {
				String Year = strDate.substring(0, 4);
				String Month = strDate.substring(4, 6);
				String Day = strDate.substring(6, 8);
				tString = Year + "-" + Month + "-" + Day;
			} else {
				System.out.println("日期格式不正确！");
			}
		}
		return tString;
	}

	/**
	 * 计算日期的函数
	 * 参数compareDate为空时：
	 * 		返回日期为基础日期增加指定年、月后的当天，或者增加指定天数后的某天。
	 *		a、如果基础日期的在当月的经过天数大于或者等于增加时间间隔后该月的总天数，则返回结果为该月月底。
	 * @param baseDate 起始日期
	 * @param interval 时间间隔
	 * @param unit 时间间隔单位
	 * @param compareDate 参照日期 参照日期指当按照年月进行日期的计算的时候，参考的日期
	 * @return Date类型变量
	 */
	public static Date calOFDate(Date baseDate, int interval, String unit, Date compareDate)
	{
		Date returnDate = null;

		GregorianCalendar mCalendar = new GregorianCalendar();
		//设置起始日期格式
		mCalendar.setTime(baseDate);
		if (unit.equals("Y"))
		{
			mCalendar.add(Calendar.YEAR, interval);
		}
		if (unit.equals("M"))
		{
			//执行月份增减
			mCalendar.add(Calendar.MONTH, interval);
		}
		if (unit.equals("D"))
		{
			mCalendar.add(Calendar.DATE, interval);
		}

		if (compareDate != null)
		{
			GregorianCalendar tCompCalendar = new GregorianCalendar();
			tCompCalendar.setTime(compareDate);
			int nBaseYears = mCalendar.get(Calendar.YEAR);
			int nBaseMonths = mCalendar.get(Calendar.MONTH);
			int nCompMonths = tCompCalendar.get(Calendar.MONTH);
			int nCompDays = tCompCalendar.get(Calendar.DATE);

			if (unit.equals("Y"))
			{
				//tCompCalendar.set(nBaseYears, nCompMonths, nCompDays);
				tCompCalendar = getFormatDate(nBaseYears,nCompMonths,nCompDays);
				if (tCompCalendar.before(mCalendar))
				{
					//tBaseCalendar.set(nBaseYears + 1, nCompMonths, nCompDays);
					mCalendar = getFormatDate(nBaseYears + 1, nCompMonths, nCompDays);
					returnDate = mCalendar.getTime();
				}
				else
				{
					tCompCalendar = getFormatDate(nBaseYears,nCompMonths,nCompDays);
					returnDate = tCompCalendar.getTime();
				}
			}
			if (unit.equals("M"))
			{
				tCompCalendar.set(nBaseYears, nBaseMonths, nCompDays);
				if (tCompCalendar.before(mCalendar))
				{
					//tBaseCalendar.set(nBaseYears, nBaseMonths + 1, nCompDays);
					mCalendar = getFormatDate(nBaseYears, nBaseMonths + 1, nCompDays);
					returnDate = mCalendar.getTime();
				}
				else
				{
					tCompCalendar = getFormatDate(nBaseYears,nBaseMonths,nCompDays);
					returnDate = tCompCalendar.getTime();
				}
			}
			if (unit.equals("D"))
			{
				returnDate = mCalendar.getTime();
			}
			tCompCalendar = null;
		}
		else
		{
			returnDate = mCalendar.getTime();
		}

		return returnDate;
	}
	/**
	 * 对传入的Year、Month、Day进行组装并格式化，并且如果Day大于本月最大日期，则重置Day为本月最后一天
	 * @param tYear
	 * @param tMonth
	 * @param tDay
	 * @return GregorianCalendar
	 */
	public static GregorianCalendar getFormatDate(int tYear,int tMonth,int tDay){
		GregorianCalendar tCalendar = new GregorianCalendar();
		int arrComnYearEndDate[] = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
		int arrLeapYearEndDate[] = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
		tCalendar.set(Calendar.YEAR,tYear);
		tCalendar.set(Calendar.MONTH,tMonth);
		if(isLeapYear(tYear+tMonth/11) && tDay > arrLeapYearEndDate[tMonth%11]){
			tCalendar.set(Calendar.DATE, arrLeapYearEndDate[tMonth%11]);
		}else if(!isLeapYear(tYear+tMonth/11) && tDay > arrComnYearEndDate[tMonth%11]){
			tCalendar.set(Calendar.DATE, arrComnYearEndDate[tMonth%11]);
		}else{
			tCalendar.set(Calendar.DATE,tDay);
		}
		return tCalendar;
	}
	/**
	 * 判断是否为闰年
	 * XinYQ added on 2006-09-25
	 */
	public static boolean isLeapYear(int nYear)
	{
		boolean ResultLeap = false;
		ResultLeap = (nYear % 400 == 0) | (nYear % 100 != 0) & (nYear % 4 == 0);
		return ResultLeap;
	}

	/**
	 * 根据给定的日期格式 将String转为Date
	 * @param Date
	 * @param pattar  yyyyMMdd 或yyyy-MM-dd
	 * @return
	 */
	public static Date FormatDate(String Date,String pattar)
	{
		Calendar calendar = null ;
		try {
			calendar = Calendar.getInstance(Locale.CHINA) ;
			calendar.setTime(new SimpleDateFormat(pattar).parse(Date)) ;
		} catch (ParseException e) {
			e.printStackTrace();
		}

		return calendar.getTime() ;
	}
	/**
	 * 通过起始日期和终止日期计算以时间间隔单位为计量标准的时间间隔 author: HST
	 * <p><b>Example: </b><p>
	 * <p>参照calInterval(String  cstartDate, String  cendDate, String unit)，前两个变量改为日期型即可<p>
	 * @param startDate 起始日期，Date变量
	 * @param endDate 终止日期，Date变量
	 * @param unit 时间间隔单位，可用值("Y"--年 "M"--月 "D"--日)
	 * @return 时间间隔,整形变量int
	 */
	public static int calInterval(Date startDate, Date endDate, String unit) {
		int interval = 0;

		GregorianCalendar sCalendar = new GregorianCalendar();
		sCalendar.setTime(startDate);
		int sYears = sCalendar.get(Calendar.YEAR);
		int sMonths = sCalendar.get(Calendar.MONTH);
		int sDays = sCalendar.get(Calendar.DAY_OF_MONTH);
		int sDaysOfYear = sCalendar.get(Calendar.DAY_OF_YEAR);

		GregorianCalendar eCalendar = new GregorianCalendar();
		eCalendar.setTime(endDate);
		int eYears = eCalendar.get(Calendar.YEAR);
		int eMonths = eCalendar.get(Calendar.MONTH);
		int eDays = eCalendar.get(Calendar.DAY_OF_MONTH);
		int eDaysOfYear = eCalendar.get(Calendar.DAY_OF_YEAR);

		if (unit.equals("Y")) {
			interval = eYears - sYears;
			if (eDaysOfYear < sDaysOfYear) {
				interval--;
			}
		}
		if (unit.equals("M")) {
			interval = eYears - sYears;
			interval = interval * 12;

			interval = eMonths - sMonths + interval;
			if (eDays < sDays) {
				interval--;
			}
		}
		if (unit.equals("D")) {
			interval = eYears - sYears;
			interval = interval * 365;

			interval = eDaysOfYear - sDaysOfYear + interval;

			// 处理润年
			int n = 0;
			eYears--;
			if (eYears > sYears) {
				int i = sYears % 4;
				if (i == 0) {
					sYears++;
					n++;
				}
				int j = (eYears) % 4;
				if (j == 0) {
					eYears--;
					n++;
				}
				n += (eYears - sYears) / 4;
			}
			if (eYears == sYears) {
				int i = sYears % 4;
				if (i == 0) {
					n++;
				}
			}
			interval += n;
		}
		return interval;
	}




	public static String getDateStr(Calendar pCalendar, String pFormat) {
		return new SimpleDateFormat(pFormat).format(pCalendar.getTime());
	}

	/**
	 * yyyyMMdd --> yyyy-MM-dd
	 */
	public static String date8to10(String pDate) {
		return formatTrans(pDate, "yyyyMMdd", "yyyy-MM-dd");
	}

	public static String formatTrans(String pDate, String pOldFormat, String pNewFormat) {
		return getDateStr(parseDate(pDate, pOldFormat), pNewFormat);
	}

	public static Date parseDate(String pDateString, String pDateFormat) {
		try {
			return new SimpleDateFormat(pDateFormat).parse(pDateString);
		} catch (ParseException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static String getDateStr(Date pDate, String pFormat) {
		return new SimpleDateFormat(pFormat).format(pDate);
	}

	public static void main(String[] args) {
		Calendar mCalendar = Calendar.getInstance();

		System.out.println( DateUtil.format(mCalendar, "yyyy/yyyyMM"));
	}
}
