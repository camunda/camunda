/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import org.camunda.operate.Metrics;

public class MetricsCounterImportListener implements ImportListener {
  
  private String counterName;
  private String importType;
  private Metrics metrics;

  public MetricsCounterImportListener(Metrics metrics,String counterName,String importType) {
    this.metrics = metrics;
    this.counterName = counterName;
    this.importType = importType;
  }
  
  @Override
  public void finished(int count) {
    metrics.recordCounts(this.counterName,count,Metrics.TAG_KEY_TYPE,importType,Metrics.TAG_KEY_STATUS,Metrics.TAG_VALUE_SUCCEEDED);
  }

  @Override
  public void failed(int count) {
    metrics.recordCounts(this.counterName,count,Metrics.TAG_KEY_TYPE,importType,Metrics.TAG_KEY_STATUS,Metrics.TAG_VALUE_FAILED);
  }

}
