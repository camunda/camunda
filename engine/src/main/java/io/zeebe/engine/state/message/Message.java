/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.message;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.LongProperty;
import io.zeebe.msgpack.property.StringProperty;
import org.agrona.DirectBuffer;

public final class Message extends UnpackedObject implements DbValue {

  private final LongProperty keyProp = new LongProperty("key", 0L);
  private final LongProperty timeToLiveProp = new LongProperty("timeToLive", 0L);
  private final LongProperty deadlineProp = new LongProperty("deadline", 0L);

  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty correlationKeyProp = new StringProperty("correlationKey", "");
  private final StringProperty variablesProp = new StringProperty("variables", "");
  private final StringProperty idProp = new StringProperty("id", "");

  public Message() {
    declareProperty(keyProp)
        .declareProperty(timeToLiveProp)
        .declareProperty(deadlineProp)
        .declareProperty(nameProp)
        .declareProperty(correlationKeyProp)
        .declareProperty(variablesProp)
        .declareProperty(idProp);
  }

  public Message(
      final long key,
      final DirectBuffer name,
      final DirectBuffer correlationKey,
      final DirectBuffer variables,
      final DirectBuffer id,
      final long timeToLive,
      final long deadline) {
    this();

    nameProp.setValue(name);
    correlationKeyProp.setValue(correlationKey);
    variablesProp.setValue(variables);
    idProp.setValue(id);

    keyProp.setValue(key);
    timeToLiveProp.setValue(timeToLive);
    deadlineProp.setValue(deadline);
  }

  public DirectBuffer getName() {
    return nameProp.getValue();
  }

  public DirectBuffer getCorrelationKey() {
    return correlationKeyProp.getValue();
  }

  public DirectBuffer getVariables() {
    return variablesProp.getValue();
  }

  public DirectBuffer getId() {
    return idProp.getValue();
  }

  public long getTimeToLive() {
    return timeToLiveProp.getValue();
  }

  public long getDeadline() {
    return deadlineProp.getValue();
  }

  public long getKey() {
    return keyProp.getValue();
  }
}
