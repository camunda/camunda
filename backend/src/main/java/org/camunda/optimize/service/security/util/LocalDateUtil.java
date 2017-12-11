package org.camunda.optimize.service.security.util;

import java.time.OffsetDateTime;

public class LocalDateUtil {

  private volatile static OffsetDateTime CURRENT_TIME = null;

  public static void setCurrentTime(OffsetDateTime currentTime) {
    LocalDateUtil.CURRENT_TIME = currentTime;
  }

  public static void reset() {
    LocalDateUtil.CURRENT_TIME = null;
  }

  public static OffsetDateTime getCurrentDateTime() {
    if (CURRENT_TIME != null) {
      return CURRENT_TIME;
    }
    return OffsetDateTime.now();
  }

}
