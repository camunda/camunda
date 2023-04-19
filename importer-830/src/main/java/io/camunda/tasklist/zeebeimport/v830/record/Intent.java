/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.zeebeimport.v830.record;

public enum Intent implements io.camunda.zeebe.protocol.record.intent.Intent {
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

  TIMED_OUT,

  FAILED,

  RETRIES_UPDATED,

  CANCELED,

  // VARIABLE
  UPDATED,

  UNKNOWN;

  private final short value = 0;

  public short value() {
    return value;
  }
}
