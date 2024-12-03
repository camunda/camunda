/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.deployment.PersistedForm;
import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.test.util.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
public class FormCreatedApplierTest {

  private static final String TENANT_1 = "tenant1";
  private static final String TENANT_2 = "tenant2";
  private KeyGenerator keyGenerator;
  private MutableProcessingState processingState;
  private MutableFormState formState;
  private FormCreatedApplier formCreatedApplier;

  @BeforeEach
  public void setup() {
    formState = processingState.getFormState();
    formCreatedApplier = new FormCreatedApplier(formState);
    keyGenerator = processingState.getKeyGenerator();
  }

  @Test
  void shouldStoreForm() {
    // given
    final var formRecord = sampleFormRecord();
    formCreatedApplier.applyState(formRecord.getFormKey(), formRecord);

    // when
    final var maybePersistedForm = formState.findFormByKey(formRecord.getFormKey(), TENANT_1);

    // then
    assertThat(maybePersistedForm).isNotEmpty();
    final var persistedForm = maybePersistedForm.get();
    assertThat(bufferAsString(persistedForm.getFormId())).isEqualTo(formRecord.getFormId());
    assertThat(persistedForm.getVersion()).isEqualTo(formRecord.getVersion());
    assertThat(persistedForm.getFormKey()).isEqualTo(formRecord.getFormKey());
    assertThat(bufferAsString(persistedForm.getResourceName()))
        .isEqualTo(formRecord.getResourceName());
    assertThat(bufferAsArray(persistedForm.getChecksum())).isEqualTo(formRecord.getChecksum());
    assertThat(bufferAsArray(persistedForm.getResource())).isEqualTo(formRecord.getResource());
  }

  @Test
  void shouldFindLatestByFormId() {
    // given
    final var formRecord1 = sampleFormRecord();
    formCreatedApplier.applyState(formRecord1.getFormKey(), formRecord1);

    final var formRecord2 = sampleFormRecord(2L, "form-id", 2, TENANT_1);
    formCreatedApplier.applyState(formRecord2.getFormKey(), formRecord2);

    // when
    final var maybePersistedForm = formState.findLatestFormById(formRecord1.getFormId(), TENANT_1);

    // then
    assertThat(maybePersistedForm).isNotEmpty();
    final var persistedForm = maybePersistedForm.get();
    assertThat(bufferAsString(persistedForm.getFormId())).isEqualTo(formRecord2.getFormId());
    assertThat(persistedForm.getVersion()).isEqualTo(2);
    assertThat(persistedForm.getFormKey()).isEqualTo(formRecord2.getFormKey());
    assertThat(bufferAsString(persistedForm.getResourceName()))
        .isEqualTo(formRecord2.getResourceName());
    assertThat(bufferAsArray(persistedForm.getChecksum())).isEqualTo(formRecord2.getChecksum());
    assertThat(bufferAsArray(persistedForm.getResource())).isEqualTo(formRecord2.getResource());
  }

  @Test
  public void shouldPutFormForDifferentTenants() {
    // given
    final long formKey = keyGenerator.nextKey();
    final String formId = Strings.newRandomValidBpmnId();
    final int version = 1;
    final var tenant1Form = sampleFormRecord(formKey, formId, version, TENANT_1);
    final var tenant2Form = sampleFormRecord(formKey, formId, version, TENANT_2);

    // when
    formCreatedApplier.applyState(tenant1Form.getFormKey(), tenant1Form);
    formCreatedApplier.applyState(tenant2Form.getFormKey(), tenant2Form);

    // then
    var form1 = formState.findFormByKey(formKey, TENANT_1).orElseThrow();
    var form2 = formState.findFormByKey(formKey, TENANT_2).orElseThrow();
    assertPersistedForm(form1, formKey, formId, version, TENANT_1);
    assertPersistedForm(form2, formKey, formId, version, TENANT_2);

    form1 = formState.findLatestFormById(formId, TENANT_1).orElseThrow();
    form2 = formState.findLatestFormById(formId, TENANT_2).orElseThrow();
    assertPersistedForm(form1, formKey, formId, version, TENANT_1);
    assertPersistedForm(form2, formKey, formId, version, TENANT_2);
  }

  private FormRecord sampleFormRecord() {
    return sampleFormRecord(1L, "form-id", 1, TENANT_1);
  }

  private FormRecord sampleFormRecord(
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
