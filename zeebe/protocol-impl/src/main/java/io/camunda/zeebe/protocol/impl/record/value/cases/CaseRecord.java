/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.cases;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.CaseRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class CaseRecord extends UnifiedRecordValue implements CaseRecordValue {
  private final LongProperty processInstanceKeyProp = new LongProperty("processInstanceKey", -1L);
  private final StringProperty titleProp = new StringProperty("title", "");

  public CaseRecord() {
    super(2);
    declareProperty(processInstanceKeyProp).declareProperty(titleProp);
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProp.getValue();
  }

  public CaseRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProp.setValue(processInstanceKey);
    return this;
  }

  @Override
  public String getTitle() {
    return BufferUtil.bufferAsString(titleProp.getValue());
  }

  public CaseRecord setTitle(final String title) {
    titleProp.setValue(title);
    return this;
  }
}
