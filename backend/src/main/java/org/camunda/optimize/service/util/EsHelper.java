/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;


public class EsHelper {

  public static String constructKey(String elasticSearchType, String engineAlias) {
    return elasticSearchType + "-" + engineAlias;
  }

}
