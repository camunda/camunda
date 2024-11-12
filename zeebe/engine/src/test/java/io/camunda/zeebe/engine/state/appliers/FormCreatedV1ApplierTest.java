/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.test.util.Strings;
import org.junit.jupiter.api.Test;

public class FormCreatedV1ApplierTest extends AbstractFormCreatedApplierTest {

  @Test
  void shouldPutFormForDifferentTenants() {
    // given
    final var formKey = keyGenerator.nextKey();
    final var deploymentKey = keyGenerator.nextKey();
    final var formId = Strings.newRandomValidBpmnId();
    final var version = 1;
    final var tenant1Form = sampleFormRecord(formKey, formId, version, deploymentKey, TENANT_1);
    final var tenant2Form = sampleFormRecord(formKey, formId, version, deploymentKey, TENANT_2);

    // when
    formCreatedApplier.applyState(tenant1Form.getFormKey(), tenant1Form);
    formCreatedApplier.applyState(tenant2Form.getFormKey(), tenant2Form);

    // then
    var form1 = formState.findFormByKey(formKey, TENANT_1).orElseThrow();
    var form2 = formState.findFormByKey(formKey, TENANT_2).orElseThrow();
    assertPersistedForm(form1, formKey, formId, version, deploymentKey, TENANT_1);
    assertPersistedForm(form2, formKey, formId, version, deploymentKey, TENANT_2);

    form1 = formState.findLatestFormById(wrapString(formId), TENANT_1).orElseThrow();
    form2 = formState.findLatestFormById(wrapString(formId), TENANT_2).orElseThrow();
    assertPersistedForm(form1, formKey, formId, version, deploymentKey, TENANT_1);
    assertPersistedForm(form2, formKey, formId, version, deploymentKey, TENANT_2);
  }

  @Override
  TypedEventApplier<FormIntent, FormRecord> createEventApplier(final MutableFormState formState) {
    return new FormCreatedV1Applier(formState);
  }
}
