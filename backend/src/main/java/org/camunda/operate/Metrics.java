/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate;

import org.camunda.operate.property.OperateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.stereotype.Component;

import com.google.common.base.Predicate;

import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;

@Component
public class Metrics implements MeterRegistryCustomizer<MeterRegistry>{
  
  Logger logger = LoggerFactory.getLogger(Metrics.class);
  @Autowired
  OperateProperties operateProperties;
  
  public static final String OPERATE_NAMESPACE = "operate.";

  private static Predicate<Id> IS_OPERATE = id -> id.getName().startsWith(OPERATE_NAMESPACE);
  
  @Autowired
  private MeterRegistry registry;

  @Override
  public void customize(MeterRegistry registry) {
    if(!isEnabled()) {
      logger.info("Metrics are disabled");
      registry.close();
      return;
    }
    logger.info("Metrics are enabled (only for "+OPERATE_NAMESPACE+"*) meters");
    registry.config()
      .meterFilter(MeterFilter.denyUnless(IS_OPERATE));   
  }

  public void recordCounts(String name, int count, String ... dimensions) {
    registry.counter(OPERATE_NAMESPACE+name, dimensions).increment(count); 
  }
 
  public boolean isEnabled() {
    return operateProperties.metricsEnabled();
  }
}
