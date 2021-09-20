/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.element;

/**
 * A representation of an element that is based on a job and should be processed by a job worker.
 * For example, a service task.
 */
public interface ExecutableJobWorkerElement extends ExecutableFlowElement {

  JobWorkerProperties getJobWorkerProperties();

  void setJobWorkerProperties(JobWorkerProperties jobWorkerProperties);
}
