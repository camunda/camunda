package org.camunda.operate.zeebeimport.record;

public enum Intent implements io.zeebe.protocol.intent.Intent {

  CREATED,

  RESOLVED,

  SEQUENCE_FLOW_TAKEN,
  GATEWAY_ACTIVATED,

  ELEMENT_READY,
  ELEMENT_ACTIVATED,
  ELEMENT_COMPLETING,
  ELEMENT_COMPLETED,
  ELEMENT_TERMINATING,
  ELEMENT_TERMINATED,

  PAYLOAD_UPDATED,

  EVENT_TRIGGERING,
  EVENT_TRIGGERED,
  EVENT_ACTIVATING,
  EVENT_ACTIVATED,

  //JOB
  ACTIVATED,

  COMPLETED,

  TIMED_OUT,

  FAILED,

  RETRIES_UPDATED,

  CANCELED,

  UNKNOWN;

  private final short value = 0;

  public short value() {
    return value;
  }
}
