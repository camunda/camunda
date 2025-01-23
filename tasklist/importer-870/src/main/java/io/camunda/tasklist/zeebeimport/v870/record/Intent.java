/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport.v870.record;

public enum Intent implements io.camunda.zeebe.protocol.record.intent.Intent {
  CREATING,
  CREATED,

  RESOLVED,

  SEQUENCE_FLOW_TAKEN,

  ELEMENT_ACTIVATING,
  ELEMENT_ACTIVATED,
  ELEMENT_COMPLETING,
  ELEMENT_COMPLETED,
  ELEMENT_TERMINATING,
  ELEMENT_TERMINATED,

  PAYLOAD_UPDATED,

  // JOB
  ACTIVATED,

  COMPLETED,

  MIGRATED,

  TIMED_OUT,

  FAILED,

  RETRIES_UPDATED,

  CANCELED,

  // VARIABLE
  UPDATED,

  // FORM, PROCESS
  DELETED,
  RECURRED_AFTER_BACKOFF,
  ASSIGNED,
  UNKNOWN;

  private final short value = 0;

  @Override
  public short value() {
    return value;
  }

  @Override
  public boolean isEvent() {
    return false;
  }
}
