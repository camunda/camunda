/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateRule;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.test.util.Strings;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class FormStateMultiTenantTest {

  private static final String TENANT_1 = "tenant1";
  private static final String TENANT_2 = "tenant2";
  @Rule public final ProcessingStateRule stateRule = new ProcessingStateRule();

  private MutableFormState formState;
  private MutableProcessingState processingState;
  private KeyGenerator keyGenerator;

  @Before
  public void setUp() {
    processingState = stateRule.getProcessingState();
    formState = processingState.getFormState();
    keyGenerator = processingState.getKeyGenerator();
  }

  @Test
  public void shouldPutFormForDifferentTenants() {
    final long formKey = keyGenerator.nextKey();
    final String formId = Strings.newRandomValidBpmnId();
    final int version = 1;
    final var tenant1Form = createFormRecord(formKey, formId, version, TENANT_1);
    final var tenant2Form = createFormRecord(formKey, formId, version, TENANT_2);

    // when
    formState.storeFormRecord(tenant1Form);
    formState.storeFormRecord(tenant2Form);

    // then
    var form1 = formState.findFormByKey(formKey, TENANT_1).orElseThrow();
    var form2 = formState.findFormByKey(formKey, TENANT_2).orElseThrow();
    assertPersistedForm(form1, formKey, formId, version, TENANT_1);
    assertPersistedForm(form2, formKey, formId, version, TENANT_2);

    form1 = formState.findLatestFormById(wrapString(formId), TENANT_1).orElseThrow();
    form2 = formState.findLatestFormById(wrapString(formId), TENANT_2).orElseThrow();
    assertPersistedForm(form1, formKey, formId, version, TENANT_1);
    assertPersistedForm(form2, formKey, formId, version, TENANT_2);
  }

  private FormRecord createFormRecord(
      final long key, final String id, final int version, final String tenant) {
    return new FormRecord()
        .setFormKey(key)
        .setFormId(id)
        .setVersion(version)
        .setResourceName("resourceName")
        .setResource(wrapString("resource"))
        .setChecksum(wrapString("checksum"))
        .setTenantId(tenant);
  }

  private void assertPersistedForm(
      final PersistedForm persistedForm,
      final long expectedKey,
      final String expectedId,
      final int expectedVersion,
      final String expectedTenant) {
    assertThat(persistedForm)
        .extracting(
            PersistedForm::getFormKey,
            form -> bufferAsString(form.getFormId()),
            PersistedForm::getVersion,
            PersistedForm::getTenantId)
        .describedAs("Gets correct form for tenant")
        .containsExactly(expectedKey, expectedId, expectedVersion, expectedTenant);
  }
}
