/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.policyupdate;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.record.value.PolicyDeleteEntryValue;
import io.camunda.zeebe.protocol.record.value.PolicyUpdateEntityType;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class PolicyDeleteEntry extends ObjectValue implements PolicyDeleteEntryValue {

  private final EnumProperty<PolicyUpdateEntityType> entityTypeProp =
      new EnumProperty<>(
          "entityType", PolicyUpdateEntityType.class, PolicyUpdateEntityType.UNSPECIFIED);
  private final StringProperty idProp = new StringProperty("id", "");

  public PolicyDeleteEntry() {
    super(2);
    declareProperty(entityTypeProp).declareProperty(idProp);
  }

  @Override
  public PolicyUpdateEntityType getEntityType() {
    return entityTypeProp.getValue();
  }

  public PolicyDeleteEntry setEntityType(final PolicyUpdateEntityType entityType) {
    entityTypeProp.setValue(entityType);
    return this;
  }

  @Override
  public String getId() {
    return BufferUtil.bufferAsString(idProp.getValue());
  }

  public PolicyDeleteEntry setId(final String id) {
    idProp.setValue(id);
    return this;
  }
}
