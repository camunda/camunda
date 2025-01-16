/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.security.identity;

public enum IdentityPermission {
  READ,
  READ_DECISION_INSTANCE,
  READ_PROCESS_DEFINITION,
  READ_PROCESS_INSTANCE,
  DELETE,
  UPDATE_PROCESS_INSTANCE,
  DELETE_PROCESS_INSTANCE,
  DELETE_DECISION_INSTANCE,
  DELETE_PROCESS;
}
