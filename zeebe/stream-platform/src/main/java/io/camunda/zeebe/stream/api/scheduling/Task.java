/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.api.scheduling;

/** Here the interface is just a suggestion. Can be whatever PDT team thinks is best to work with */
public interface Task {

  TaskResult execute(TaskResultBuilder taskResultBuilder);
}
