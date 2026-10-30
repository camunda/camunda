/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.job;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.ConsentGround;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

/**
 * What a gated job needs before it may be handed out, recorded when the job is created: the ground
 * it rests on, the connection and operation it acts on, whose record changes, who asked, and the
 * value an approval shows and binds. A job with ground {@link ConsentGround#NONE} is not gated.
 */
public final class JobConsent extends ObjectValue {

  private static final StringValue GROUND_KEY = new StringValue("ground");
  private static final StringValue CONNECTION_KEY = new StringValue("connection");
  private static final StringValue OPERATION_KEY = new StringValue("operation");
  private static final StringValue SUBJECT_KEY = new StringValue("subject");
  private static final StringValue REQUESTER_KEY = new StringValue("requester");
  private static final StringValue TARGET_KEY = new StringValue("target");
  private static final StringValue CHANGE_KEY = new StringValue("change");

  private final EnumProperty<ConsentGround> groundProp =
      new EnumProperty<>(GROUND_KEY, ConsentGround.class, ConsentGround.NONE);
  private final StringProperty connectionProp = new StringProperty(CONNECTION_KEY, "");
  private final StringProperty operationProp = new StringProperty(OPERATION_KEY, "");
  private final StringProperty subjectProp = new StringProperty(SUBJECT_KEY, "");
  private final StringProperty requesterProp = new StringProperty(REQUESTER_KEY, "");
  // the subject's id in the target system, named by the task on the relationship ground
  private final StringProperty targetProp = new StringProperty(TARGET_KEY, "");
  private final DocumentProperty changeProp = new DocumentProperty(CHANGE_KEY);

  public JobConsent() {
    super(7);
    declareProperty(groundProp)
        .declareProperty(connectionProp)
        .declareProperty(operationProp)
        .declareProperty(subjectProp)
        .declareProperty(requesterProp)
        .declareProperty(targetProp)
        .declareProperty(changeProp);
  }

  public void copyFrom(final JobConsent other) {
    groundProp.setValue(other.getGround());
    connectionProp.setValue(other.connectionProp.getValue());
    operationProp.setValue(other.operationProp.getValue());
    subjectProp.setValue(other.subjectProp.getValue());
    requesterProp.setValue(other.requesterProp.getValue());
    targetProp.setValue(other.targetProp.getValue());
    changeProp.setValue(other.changeProp.getValue());
  }

  @JsonIgnore
  public boolean isGated() {
    return getGround() != ConsentGround.NONE;
  }

  public ConsentGround getGround() {
    return groundProp.getValue();
  }

  public JobConsent setGround(final ConsentGround ground) {
    groundProp.setValue(ground);
    return this;
  }

  public String getConnection() {
    return BufferUtil.bufferAsString(connectionProp.getValue());
  }

  public JobConsent setConnection(final String connection) {
    connectionProp.setValue(connection);
    return this;
  }

  public String getOperation() {
    return BufferUtil.bufferAsString(operationProp.getValue());
  }

  public JobConsent setOperation(final String operation) {
    operationProp.setValue(operation);
    return this;
  }

  public String getSubject() {
    return BufferUtil.bufferAsString(subjectProp.getValue());
  }

  public JobConsent setSubject(final String subject) {
    subjectProp.setValue(subject);
    return this;
  }

  public String getRequester() {
    return BufferUtil.bufferAsString(requesterProp.getValue());
  }

  public JobConsent setRequester(final String requester) {
    requesterProp.setValue(requester);
    return this;
  }

  public String getTarget() {
    return BufferUtil.bufferAsString(targetProp.getValue());
  }

  public JobConsent setTarget(final String target) {
    targetProp.setValue(target);
    return this;
  }

  public Map<String, Object> getChange() {
    return MsgPackConverter.convertToMap(changeProp.getValue());
  }

  @JsonIgnore
  public DirectBuffer getChangeBuffer() {
    return changeProp.getValue();
  }

  public JobConsent setChange(final DirectBuffer change) {
    changeProp.setValue(change);
    return this;
  }
}
