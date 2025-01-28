/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter;

import org.camunda.bpm.client.spring.annotation.ExternalTaskSubscription;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@ExternalTaskSubscription(topicName = "test-topic")
@Component
public class SampleExternalTaskHandler implements ExternalTaskHandler {
  public static final Logger LOG = LoggerFactory.getLogger(SampleExternalTaskHandler.class);
  public static String someVariable;
  public static boolean executed = false;

  @Override
  public void execute(
      final ExternalTask externalTask, final ExternalTaskService externalTaskService) {
    LOG.info("Called from process instance {}", externalTask.getProcessInstanceId());
    someVariable = externalTask.getVariable("someVariable");
    executed = true;
    externalTaskService.complete(externalTask);
  }
}
