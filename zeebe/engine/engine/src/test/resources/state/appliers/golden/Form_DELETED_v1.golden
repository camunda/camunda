/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.record.intent.FormIntent;

public class FormDeletedApplier implements TypedEventApplier<FormIntent, FormRecord> {

  private final MutableFormState formState;

  public FormDeletedApplier(final MutableFormState formState) {
    this.formState = formState;
  }

  @Override
  public void applyState(final long key, final FormRecord value) {
    formState.deleteFormInFormsColumnFamily(value);
    formState.deleteFormInFormByIdAndVersionColumnFamily(value);
    formState.deleteFormInFormVersionColumnFamily(value);
    formState.deleteFormInFormKeyByFormIdAndDeploymentKeyColumnFamily(value);
    formState.deleteFormInFormKeyByFormIdAndVersionTagColumnFamily(value);
  }
}
