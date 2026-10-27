/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsArray;
import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.deployment.PersistedForm;
import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(ProcessingStateExtension.class)
abstract class AbstractFormCreatedApplierTest {

  static final String TENANT_1 = "tenant1";
  static final String TENANT_2 = "tenant2";
  KeyGenerator keyGenerator;
  MutableFormState formState;
  TypedEventApplier<FormIntent, FormRecord> formCreatedApplier;

  private MutableProcessingState processingState;

  @BeforeEach
  void setup() {
    formState = processingState.getFormState();
    formCreatedApplier = createEventApplier(formState);
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
    assertThat(maybePersistedForm).hasValueSatisfying(isEqualToFormRecord(formRecord));
  }

  @Test
  void shouldFindLatestByFormId() {
    // given
    final var formRecord1 = sampleFormRecord();
    formCreatedApplier.applyState(formRecord1.getFormKey(), formRecord1);

    final var formRecord2 = sampleFormRecord(2L, "form-id", 2, 2L, TENANT_1);
    formCreatedApplier.applyState(formRecord2.getFormKey(), formRecord2);

    // when
    final var maybePersistedForm = formState.findLatestFormById(formRecord1.getFormId(), TENANT_1);

    // then
    assertThat(maybePersistedForm).hasValueSatisfying(isEqualToFormRecord(formRecord2));
  }

  FormRecord sampleFormRecord() {
    return sampleFormRecord(1L, "form-id", 1, 1L, TENANT_1);
  }

  FormRecord sampleFormRecord(
      final long key,
      final String id,
      final int version,
      final long deploymentKey,
      final String tenant) {
    return new FormRecord()
        .setFormKey(key)
        .setFormId(id)
        .setVersion(version)
        .setResourceName("resourceName")
        .setResource(wrapString("resource"))
        .setChecksum(wrapString("checksum"))
        .setTenantId(tenant)
        .setDeploymentKey(deploymentKey);
  }

  void assertPersistedForm(
      final PersistedForm persistedForm,
      final long expectedKey,
      final String expectedId,
      final int expectedVersion,
      final long expectedDeploymentKey,
      final String expectedTenant) {
    assertThat(persistedForm)
        .extracting(
            PersistedForm::getFormKey,
            form -> bufferAsString(form.getFormId()),
            PersistedForm::getVersion,
            PersistedForm::getDeploymentKey,
            PersistedForm::getTenantId)
        .describedAs("Gets correct form for tenant")
        .containsExactly(
            expectedKey, expectedId, expectedVersion, expectedDeploymentKey, expectedTenant);
  }

  Consumer<PersistedForm> isEqualToFormRecord(final FormRecord record) {
    return persistedForm -> {
      assertThat(bufferAsString(persistedForm.getFormId())).isEqualTo(record.getFormId());
      assertThat(persistedForm.getVersion()).isEqualTo(record.getVersion());
      assertThat(persistedForm.getFormKey()).isEqualTo(record.getFormKey());
      assertThat(bufferAsString(persistedForm.getResourceName()))
          .isEqualTo(record.getResourceName());
      assertThat(bufferAsArray(persistedForm.getChecksum())).isEqualTo(record.getChecksum());
      assertThat(bufferAsArray(persistedForm.getResource())).isEqualTo(record.getResource());
      assertThat(persistedForm.getDeploymentKey()).isEqualTo(record.getDeploymentKey());
      assertThat(persistedForm.getVersionTag()).isEqualTo(record.getVersionTag());
    };
  }

  abstract TypedEventApplier<FormIntent, FormRecord> createEventApplier(MutableFormState formState);
}
