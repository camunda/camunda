/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Optional;
import org.agrona.DirectBuffer;

public class DbFormState implements MutableFormState {

  @Override
  public void storeFormRecord(final FormRecord record) {
    // TODO - replace with actual DB query
  }

  @Override
  public Optional<PersistedForm> findLatestFormById(final DirectBuffer formId) {
    // TODO - replace with actual DB query
    final PersistedForm persistedForm = new PersistedForm();
    final FormRecord record =
        new FormRecord()
            .setFormKey(11111111111L)
            .setFormId("test-form-id")
            .setVersion(1)
            .setResourceName("test-form.form")
            .setChecksum(BufferUtil.wrapString("test-checksum"))
            .setResource(BufferUtil.wrapString("{}"));
    persistedForm.wrap(record);
    return Optional.of(persistedForm);
  }
}
