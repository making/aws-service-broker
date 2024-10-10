package com.example.awsservicebroker.utils;

import org.springframework.lang.Nullable;

public class StringUtils {

	@Nullable
	public static String removeHyphen(@Nullable String s) {
		if (s == null) {
			return null;
		}
		return s.replaceAll("-", "");
	}

	@Nullable
	public static String toUpperCamel(@Nullable String input) {
		if (input == null) {
			return null;
		}
		StringBuilder result = new StringBuilder();
		boolean nextUpperCase = false;
		for (char c : input.toCharArray()) {
			if (c == '_') {
				nextUpperCase = true;
			}
			else {
				if (nextUpperCase) {
					result.append(Character.toUpperCase(c));
					nextUpperCase = false;
				}
				else {
					result.append(Character.toLowerCase(c));
				}
			}
		}
		return result.toString();
	}

}
