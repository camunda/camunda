/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.es.writer;

import io.camunda.operate.entities.MetricEntity;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.webapp.es.contract.MetricContract;
import io.camunda.operate.webapp.es.dao.UsageMetricDAO;
import io.camunda.operate.webapp.es.dao.response.DAOResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;

public class MetricWriter implements MetricContract.Writer {

  private static final Logger LOGGER = LoggerFactory.getLogger(MetricWriter.class);
  public static final String EVENT_PROCESS_INSTANCE_FINISHED = "EVENT_PROCESS_INSTANCE_FINISHED";
  @Autowired private UsageMetricDAO dao;

  @Override
  public void registerProcessInstanceCompleteEvent(String processInstanceKey) {
    final MetricEntity metric = createProcessInstanceFinishedKey(processInstanceKey);
    final DAOResponse response = dao.insert(metric);
    if (response.hasError()) {
      final String message = "Wrong response status while logging event";
      LOGGER.error(message);
      throw new OperateRuntimeException(message);
    }
  }

  private MetricEntity createProcessInstanceFinishedKey(String processInstanceKey) {
    return new MetricEntity()
        .setEvent(EVENT_PROCESS_INSTANCE_FINISHED)
        .setValue(processInstanceKey)
        .setEventTime(OffsetDateTime.now());
  }
}
