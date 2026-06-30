/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The msgpack {@link DbValue} backing a {@link ProcessInstanceProjection} in RocksDB. The {@code
 * processInstanceKey} is the column-family key, so it is not stored here. The instance's variables
 * are stored as an array of name/value entries. {@link #wrap} resets first, so a single instance
 * can be reused across writes without the variables array accumulating.
 */
public final class PersistedProjection extends UnpackedObject implements DbValue {

  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final IntegerProperty versionProp = new IntegerProperty("version", -1);
  private final StringProperty tenantIdProp = new StringProperty("tenantId", "");
  private final LongProperty startTimeProp =
      new LongProperty("startTime", ProcessInstanceProjection.UNSET);
  private final LongProperty endTimeProp =
      new LongProperty("endTime", ProcessInstanceProjection.UNSET);
  private final BooleanProperty terminatedProp = new BooleanProperty("terminated", false);
  private final BooleanProperty factEmittedProp = new BooleanProperty("factEmitted", false);
  private final ArrayProperty<PersistedVariable> variablesProp =
      new ArrayProperty<>("variables", PersistedVariable::new);

  public PersistedProjection() {
    super(9);
    declareProperty(processDefinitionKeyProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(versionProp)
        .declareProperty(tenantIdProp)
        .declareProperty(startTimeProp)
        .declareProperty(endTimeProp)
        .declareProperty(terminatedProp)
        .declareProperty(factEmittedProp)
        .declareProperty(variablesProp);
  }

  public PersistedProjection wrap(final ProcessInstanceProjection projection) {
    reset(); // clear all properties (incl. the variables array) so a reused instance is safe
    processDefinitionKeyProp.setValue(projection.processDefinitionKey());
    bpmnProcessIdProp.setValue(projection.bpmnProcessId());
    versionProp.setValue(projection.version());
    tenantIdProp.setValue(projection.tenantId());
    startTimeProp.setValue(projection.startTime());
    endTimeProp.setValue(projection.endTime());
    terminatedProp.setValue(projection.terminated());
    factEmittedProp.setValue(projection.factEmitted());
    projection.variables().forEach((name, value) -> variablesProp.add().set(name, value));
    return this;
  }

  public ProcessInstanceProjection toProjection(final long processInstanceKey) {
    final Map<String, String> variables = new LinkedHashMap<>();
    for (final PersistedVariable variable : variablesProp) {
      variables.put(variable.name(), variable.value());
    }
    return new ProcessInstanceProjection(
        processInstanceKey,
        processDefinitionKeyProp.getValue(),
        BufferUtil.bufferAsString(bpmnProcessIdProp.getValue()),
        versionProp.getValue(),
        BufferUtil.bufferAsString(tenantIdProp.getValue()),
        startTimeProp.getValue(),
        endTimeProp.getValue(),
        terminatedProp.getValue(),
        factEmittedProp.getValue(),
        Map.copyOf(variables));
  }
}
