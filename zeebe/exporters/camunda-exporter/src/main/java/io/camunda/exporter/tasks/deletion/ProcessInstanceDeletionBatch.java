/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.deletion;

import java.util.List;

/**
 * A batch of finished process instances whose retention period has expired and whose data can be
 * deleted. Root process instance keys identify whole PI hierarchies, while process instance keys
 * identify individual (legacy, or non-hierarchy mode) process instances.
 */
public record ProcessInstanceDeletionBatch(
    List<Long> processInstanceKeys, List<Long> rootProcessInstanceKeys) {

  public static ProcessInstanceDeletionBatch empty() {
    return new ProcessInstanceDeletionBatch(List.of(), List.of());
  }

  public int size() {
    return processInstanceKeys.size() + rootProcessInstanceKeys.size();
  }

  public boolean isEmpty() {
    return size() == 0;
  }
}
