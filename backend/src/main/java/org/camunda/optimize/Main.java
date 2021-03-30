/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize;

import org.camunda.optimize.jetty.EmbeddedCamundaOptimize;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan(
  basePackages = {
    "org.camunda.optimize.service",
    "org.camunda.optimize.rest",
    "org.camunda.optimize.plugin"
  },
  excludeFilters = @ComponentScan.Filter(IgnoreDuringScan.class))
@Configuration
public class Main {

  private static EmbeddedCamundaOptimize jettyCamundaOptimize;

  public static void main(String[] args) throws Exception {
    jettyCamundaOptimize = new EmbeddedCamundaOptimize();
    try {
      jettyCamundaOptimize.startOptimize();
      jettyCamundaOptimize.startEngineImportSchedulers();
      jettyCamundaOptimize.join();
    } finally {
      jettyCamundaOptimize.destroyOptimize();
    }
  }

}
