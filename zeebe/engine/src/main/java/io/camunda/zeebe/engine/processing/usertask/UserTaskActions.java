/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

/**
 * Standard action values for user task lifecycle transitions. Used by both explicit command
 * processors (assign, claim, update, complete) and broker-internal transitions (create, cancel,
 * variable-triggered update, migration) where no caller-supplied action is present.
 */
public final class UserTaskActions {

  public static final String CREATE = "create";
  public static final String ASSIGN = "assign";
  public static final String CLAIM = "claim";
  public static final String UPDATE = "update";
  public static final String COMPLETE = "complete";
  public static final String CANCEL = "cancel";

  private UserTaskActions() {}
}
