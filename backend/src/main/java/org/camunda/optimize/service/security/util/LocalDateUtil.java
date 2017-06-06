package org.camunda.optimize.service.security.util;

import java.time.LocalDateTime;

public class LocalDateUtil {

  private volatile static LocalDateTime CURRENT_TIME = null;

  public static void setCurrentTime(LocalDateTime currentTime) {
    LocalDateUtil.CURRENT_TIME = currentTime;
  }

  public static void reset() {
    LocalDateUtil.CURRENT_TIME = null;
  }

  public static LocalDateTime getCurrentDateTime() {
    if (CURRENT_TIME != null) {
      return CURRENT_TIME;
    }
    return LocalDateTime.now();
  }

}
