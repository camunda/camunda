/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package org.camunda.community.migration.adapter.worker;

import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.api.worker.JobHandler;
import java.util.Optional;
import org.camunda.bpm.client.task.ExternalTask;
import org.camunda.bpm.client.task.ExternalTaskHandler;
import org.camunda.bpm.client.task.ExternalTaskService;
import org.camunda.community.migration.adapter.externalTask.JobClientWrappingExternalTaskService;
import org.camunda.community.migration.adapter.externalTask.JobWrappingExternalTask;

public class ExternalTaskHandlerWrapper implements JobHandler {
  private final ExternalTaskHandler externalTaskHandler;
  private final Optional<String> businessKeyVariableName;

  public ExternalTaskHandlerWrapper(
      ExternalTaskHandler externalTaskHandler, Optional<String> businessKeyVariableName) {
    this.externalTaskHandler = externalTaskHandler;
    this.businessKeyVariableName = businessKeyVariableName;
  }

  @Override
  public void handle(JobClient client, ActivatedJob job) {
    final ExternalTask externalTask = new JobWrappingExternalTask(job, businessKeyVariableName);
    final ExternalTaskService externalTaskService =
        new JobClientWrappingExternalTaskService(client, externalTask);
    externalTaskHandler.execute(externalTask, externalTaskService);
  }
}
