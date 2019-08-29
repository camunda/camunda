/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.zeebeimport;

import org.camunda.operate.Metrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import static org.springframework.beans.factory.config.BeanDefinition.SCOPE_PROTOTYPE;

@Component
@Scope(SCOPE_PROTOTYPE)
public class MetricsCounterImportListener implements ImportListener {
  
  private String counterName;
  private String importType;

  @Autowired
  private Metrics metrics;

  public MetricsCounterImportListener(String counterName, String importType) {
    this.counterName = counterName;
    this.importType = importType;
  }

  @Override
  public void finished(int count) {
    metrics.recordCounts(this.counterName, count, Metrics.TAG_KEY_TYPE, importType, Metrics.TAG_KEY_STATUS, Metrics.TAG_VALUE_SUCCEEDED);
  }

  @Override
  public void failed(int count) {
    metrics.recordCounts(this.counterName, count, Metrics.TAG_KEY_TYPE, importType, Metrics.TAG_KEY_STATUS, Metrics.TAG_VALUE_FAILED);
  }

}
