/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.record.intent.FormIntent;

public class FormCreatedApplier implements TypedEventApplier<FormIntent, FormRecord> {

  private final MutableFormState formState;

  public FormCreatedApplier(final MutableFormState state) {
    formState = state;
  }

  @Override
  public void applyState(final long formKey, final FormRecord value) {
    formState.storeFormInFormColumnFamily(value);
    formState.storeFormInFormByIdAndVersionColumnFamily(value);
    formState.updateLatestVersion(value);
  }
}
