/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.stream.job;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import java.util.Collection;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public final class JobActivationPropertiesImpl extends UnpackedObject
    implements JobActivationProperties {
  private final StringProperty workerProp = new StringProperty("worker", "");
  private final ArrayProperty<StringValue> fetchVariablesProp =
      new ArrayProperty<>("variables", new StringValue());
  private final LongProperty timeoutProp = new LongProperty("timeout", 5_000L);

  public JobActivationPropertiesImpl() {
    declareProperty(workerProp).declareProperty(fetchVariablesProp).declareProperty(timeoutProp);
  }

  @Override
  public DirectBuffer worker() {
    return workerProp.getValue();
  }

  @Override
  public Collection<DirectBuffer> fetchVariables() {
    return fetchVariablesProp.stream().map(StringValue::getValue).collect(Collectors.toSet());
  }

  @Override
  public long timeout() {
    return timeoutProp.getValue();
  }

  public JobActivationPropertiesImpl setWorker(final String worker) {
    workerProp.setValue(worker);
    return this;
  }

  public JobActivationPropertiesImpl setTimeout(final long timeout) {
    timeoutProp.setValue(timeout);
    return this;
  }

  public JobActivationPropertiesImpl setFetchVariables(final Collection<DirectBuffer> fetchVariables) {
    fetchVariablesProp.reset();
    for (final DirectBuffer variable : fetchVariables) {
      fetchVariablesProp.add().wrap(variable);
    }

    return this;
  }
}
