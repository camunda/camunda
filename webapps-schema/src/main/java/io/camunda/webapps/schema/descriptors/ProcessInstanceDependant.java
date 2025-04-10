/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.descriptors;

public interface ProcessInstanceDependant {

  String PROCESS_INSTANCE_KEY = "processInstanceKey";

  String getFullQualifiedName();

  default String getProcessInstanceDependantField() {
    return PROCESS_INSTANCE_KEY;
  }
}
