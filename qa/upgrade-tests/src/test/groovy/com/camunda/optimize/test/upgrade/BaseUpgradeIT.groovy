/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package com.camunda.optimize.test.upgrade

class BaseUpgradeIT {
  protected String previousVersion = System.properties.getProperty("previousVersion");
  protected String currentVersion = System.properties.getProperty("currentVersion");
  protected String buildDirectory = System.properties.getProperty("buildDirectory");
  protected Integer oldElasticPort = Integer.valueOf(System.properties.getProperty("oldElasticPort"));
  protected Integer newElasticPort = Integer.valueOf(System.properties.getProperty("newElasticPort"));
}
