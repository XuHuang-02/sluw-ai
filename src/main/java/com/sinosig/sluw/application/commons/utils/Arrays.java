package com.sinosig.sluw.application.commons.utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class Arrays {

	private Arrays(){
    	
    }
	
	@SuppressWarnings("unchecked")
	public static <T> T[] concat(T[] arr1, T[] arr2){
		if(arr1 == null || arr1.length == 0){
			return arr2;
		}
		if(arr2 == null || arr2.length == 0){
			return arr1;
		}
		T[] result = (T[])Array.newInstance(arr1.getClass().getComponentType(), arr1.length+arr2.length);
		System.arraycopy(arr1, 0, result, 0, arr1.length);
		System.arraycopy(arr2, 0, result, arr1.length, arr2.length);
		return result;
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T[] concat(T ... elements){
		return elements;
	}
	
	public static <T> T get(T[] arrays, int i) {
		if(arrays != null && i >= 0 && arrays.length>i){
			return arrays[i];
		}
		return null;
	}
	public static byte get(byte[] arrays, int i) {
		if(arrays != null && i >= 0 && arrays.length>i){
			return arrays[i];
		}
		return 0;
	}
	public static short get(short[] arrays, int i) {
		if(arrays != null && i >= 0 && arrays.length>i){
			return arrays[i];
		}
		return 0;
	}
	public static int get(int[] arrays, int i) {
		if(arrays != null && i >= 0 && arrays.length>i){
			return arrays[i];
		}
		return 0;
	}
	public static long get(long[] arrays, int i) {
		if(arrays != null && i >= 0 && arrays.length>i){
			return arrays[i];
		}
		return 0;
	}
	
	public static <T> int len(T[] arr1) {
		if(arr1 == null){
			return 0;
		}
		return arr1.length;
	}
	
	public static int len(byte[] arr1) {
		if(arr1 == null){
			return 0;
		}
		return arr1.length;
	}
	public static int len(short[] arr1) {
		if(arr1 == null){
			return 0;
		}
		return arr1.length;
	}
	public static int len(int[] arr1) {
		if(arr1 == null){
			return 0;
		}
		return arr1.length;
	}
	public static int len(long[] arr1) {
		if(arr1 == null){
			return 0;
		}
		return arr1.length;
	}
	
	
	public static <T> T get(List<T> list, int index) {
		if(list != null && index >= 0 && list.size() > index){
			return list.get(index);
		}
		return null;
	}
	
	public static int len(List<?> list) {
		if(list == null){
			return 0;
		}
		return list.size();
	}

	public static <T> List<T> toList(T[] array) {
		if(len(array) < 1) {
			return null ;
		}
		List<T> list = new ArrayList<T>();
		for(int i = 0; i < len(array); i++) {
			list.add(array[i]);
		}
		return list;
	}

	/**
	 * 将字符串分割成数组,但是去掉空数据
	 * @param string
	 * @param splitment
	 * @return
	 */
	public static String[] splitClearEmpty(String string, String splitment) {
		if(Strings.isEmpty(string)) {
			return null;
		}
		if(Strings.isEmpty(splitment)) {
			return new String[]{string};
		}
		ArrayList<String> retTemp = new ArrayList<String>();
		String[] ret = null;
		if(string != null){
			ret = string.split(splitment);
			if(ret!=null)
				for (int i = 0; i < ret.length;i++){
					if(!Strings.isEmpty(ret[i])){
						retTemp.add(ret[i].trim());
					}
				}
			if(retTemp.size() > 0){
				ret = new String[retTemp.size()];
				for( int i = 0; i < ret.length; i++){
					ret[i] = retTemp.get(i);
				}
			} else {
				ret = null;
			}
		}
		return ret;
	}

}
