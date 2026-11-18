/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.tasks.retention;

import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public interface ProcessInstanceRetentionBatch {
  List<Long> processInstanceKeys();

  List<Long> rootProcessInstanceKeys();

  /**
   * Creates a map of field names to lists of document IDs for the process instances and root
   * process instances in this batch. The field names are determined based on the provided template
   * descriptor.
   *
   * @param templateDescriptor the template descriptor used to determine the field names
   * @return a map where keys are field names and values are lists of document IDs
   */
  default Map<String, List<String>> createIdsByFieldMap(
      final IndexTemplateDescriptor templateDescriptor) {
    final Map<String, List<String>> idsMap = new HashMap<>();
    final String processInstanceKeyField;
    final String rootProcessInstanceKeyField;
    switch (templateDescriptor) {
      case final ListViewTemplate ignored -> {
        processInstanceKeyField = ListViewTemplate.PROCESS_INSTANCE_KEY;
        rootProcessInstanceKeyField = ListViewTemplate.ROOT_PROCESS_INSTANCE_KEY;
      }
      case final ProcessInstanceDependant pid -> {
        processInstanceKeyField = pid.getProcessInstanceDependantField();
        rootProcessInstanceKeyField = pid.getRootProcessInstanceKeyField();
      }
      default ->
          throw new IllegalArgumentException(
              "Unsupported template descriptor: " + templateDescriptor.getClass().getName());
    }
    if (!processInstanceKeys().isEmpty()) {
      idsMap.put(
          processInstanceKeyField, processInstanceKeys().stream().map(String::valueOf).toList());
    }
    if (!rootProcessInstanceKeys().isEmpty()) {
      idsMap.put(
          rootProcessInstanceKeyField,
          rootProcessInstanceKeys().stream().map(String::valueOf).toList());
    }
    return idsMap;
  }
}
