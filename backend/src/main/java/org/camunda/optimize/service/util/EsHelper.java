package org.camunda.optimize.service.util;


public class EsHelper {

  public static String constructKey(String elasticSearchType, String engineAlias) {
    return elasticSearchType + "-" + engineAlias;
  }

}
