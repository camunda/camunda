package org.camunda.optimize.service.util;

public class StringUtil {

  public static String[] splitStringByComma(String commaSeparatedList) {
    String[] split;
    if (commaSeparatedList != null) {
      split = commaSeparatedList.trim().split("\\s*,\\s*");
    } else {
      split = new String[0];
    }
    return split;
  }

}
