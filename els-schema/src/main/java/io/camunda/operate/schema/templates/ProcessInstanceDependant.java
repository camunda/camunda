/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.schema.templates;

public interface ProcessInstanceDependant {

  String PROCESS_INSTANCE_KEY = "processInstanceKey";

  String getFullQualifiedName();

}
