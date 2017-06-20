package org.camunda.optimize.service.util;

public class StringUtil {

  public static String[] splitStringByComma(String commaSeparatedList) {
    return commaSeparatedList.trim().split("\\s*,\\s*");
  }

}
