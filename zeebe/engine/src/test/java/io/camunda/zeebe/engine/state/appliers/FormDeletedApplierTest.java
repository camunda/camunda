/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

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
import io.camunda.zeebe.test.util.Strings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

public class FormDeletedApplierTest {

  @ExtendWith(ProcessingStateExtension.class)
  abstract static class AbstractFormDeletedApplierTest {

    static final String TENANT_1 = "tenant1";
    static final String TENANT_2 = "tenant2";
    MutableFormState formState;
    TypedEventApplier<FormIntent, FormRecord> formCreatedApplier;
    FormDeletedApplier formDeletedApplier;
    KeyGenerator keyGenerator;

    private MutableProcessingState processingState;

    @BeforeEach
    public void setup() {
      formState = processingState.getFormState();
      formCreatedApplier = createEventApplier(formState);
      formDeletedApplier = new FormDeletedApplier(formState);
      keyGenerator = processingState.getKeyGenerator();
    }

    abstract TypedEventApplier<FormIntent, FormRecord> createEventApplier(
        MutableFormState formState);

    @Test
    void shouldNotFindVersion2AsLatestFormAfterDeletingVersion2() {
      // given
      final var formV1 = sampleFormRecord();
      final var formV2 = sampleFormRecord(2L, "form-id", 2, 2L, TENANT_1);
      formCreatedApplier.applyState(formV1.getFormKey(), formV1);
      formCreatedApplier.applyState(formV2.getFormKey(), formV2);

      final var latestFormOpt =
          formState.findLatestFormById(formV2.getFormId(), formV2.getTenantId());
      assertThat(latestFormOpt).isNotEmpty();
      assertThat(latestFormOpt.get().getVersion()).isEqualTo(2);

      // when
      formDeletedApplier.applyState(formV2.getFormKey(), formV2);

      // then
      final var latestFormV1Opt =
          formState.findLatestFormById(formV2.getFormId(), formV2.getTenantId());
      assertThat(latestFormV1Opt).isNotEmpty();
      assertThat(latestFormV1Opt.get().getVersion()).isEqualTo(1);
    }

    @Test
    void shouldFindVersion2AsLatestFormAfterDeletingVersion1() {
      // given
      final var formV1 = sampleFormRecord();
      final var formV2 = sampleFormRecord(2L, "form-id", 2, 2L, TENANT_1);
      formCreatedApplier.applyState(formV1.getFormKey(), formV1);
      formCreatedApplier.applyState(formV2.getFormKey(), formV2);

      final var latestFormOpt =
          formState.findLatestFormById(formV2.getFormId(), formV2.getTenantId());
      assertThat(latestFormOpt).isNotEmpty();
      assertThat(latestFormOpt.get().getVersion()).isEqualTo(2);

      // when
      formDeletedApplier.applyState(formV1.getFormKey(), formV1);

      // then
      final var latestFormV2Opt =
          formState.findLatestFormById(formV2.getFormId(), formV2.getTenantId());
      assertThat(latestFormV2Opt).isNotEmpty();
      assertThat(latestFormV2Opt.get().getVersion()).isEqualTo(2);
    }

    @Test
    void shouldNotReuseADeletedVersionNumber() {
      // given
      final var form = sampleFormRecord();
      formCreatedApplier.applyState(form.getFormKey(), form);
      final var formV2 = sampleFormRecord(2L, "form-id", 2, 2L, TENANT_1);
      formCreatedApplier.applyState(formV2.getFormKey(), formV2);

      // when
      formDeletedApplier.applyState(form.getFormKey(), form);
      formDeletedApplier.applyState(formV2.getFormKey(), formV2);

      // then
      final var nextAvailableVersion =
          formState.getNextFormVersion(form.getFormId(), form.getTenantId());

      assertThat(nextAvailableVersion).isEqualTo(3);
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
  }

  @Nested
  class WithFormCreatedV1Applier extends AbstractFormDeletedApplierTest {

    @Override
    TypedEventApplier<FormIntent, FormRecord> createEventApplier(final MutableFormState formState) {
      return new FormCreatedV1Applier(formState);
    }

    @Test
    void shouldDeleteFormForSpecificTenant() {
      // given
      final var formKey = keyGenerator.nextKey();
      final var deploymentKey = keyGenerator.nextKey();
      final var formId = Strings.newRandomValidBpmnId();
      final var version = 1;
      final var tenant1Form = sampleFormRecord(formKey, formId, version, deploymentKey, TENANT_1);
      final var tenant2Form = sampleFormRecord(formKey, formId, version, deploymentKey, TENANT_2);

      formCreatedApplier.applyState(tenant1Form.getFormKey(), tenant1Form);
      formCreatedApplier.applyState(tenant2Form.getFormKey(), tenant2Form);

      // when
      formDeletedApplier.applyState(tenant1Form.getFormKey(), tenant1Form);

      // then
      assertThat(formState.findLatestFormById(tenant1Form.getFormId(), TENANT_1)).isEmpty();
      assertThat(formState.findLatestFormById(tenant2Form.getFormId(), TENANT_2)).isNotEmpty();
    }
  }

  @Nested
  class WithFormCreatedV2Applier extends AbstractFormDeletedApplierTest {

    @Override
    TypedEventApplier<FormIntent, FormRecord> createEventApplier(final MutableFormState formState) {
      return new FormCreatedV2Applier(formState);
    }

    @Test
    void shouldDeleteFormForSpecificTenant() {
      // given
      final var formKey = keyGenerator.nextKey();
      final var deploymentKey = keyGenerator.nextKey();
      final var formId = Strings.newRandomValidBpmnId();
      final var version = 1;
      final var versionTag = "v1.0";
      final var tenant1Form =
          sampleFormRecord(formKey, formId, version, deploymentKey, TENANT_1)
              .setVersionTag(versionTag);
      final var tenant2Form =
          sampleFormRecord(formKey, formId, version, deploymentKey, TENANT_2)
              .setVersionTag(versionTag);

      formCreatedApplier.applyState(tenant1Form.getFormKey(), tenant1Form);
      formCreatedApplier.applyState(tenant2Form.getFormKey(), tenant2Form);

      // when
      formDeletedApplier.applyState(tenant1Form.getFormKey(), tenant1Form);

      // then
      assertThat(formState.findLatestFormById(tenant1Form.getFormId(), TENANT_1)).isEmpty();
      assertThat(formState.findLatestFormById(tenant2Form.getFormId(), TENANT_2)).isNotEmpty();
      assertThat(
              formState.findFormByIdAndDeploymentKey(
                  tenant1Form.getFormId(), tenant1Form.getDeploymentKey(), TENANT_1))
          .isEmpty();
      assertThat(
              formState.findFormByIdAndDeploymentKey(
                  tenant2Form.getFormId(), tenant2Form.getDeploymentKey(), TENANT_2))
          .isNotEmpty();
      assertThat(
              formState.findFormByIdAndVersionTag(
                  tenant1Form.getFormId(), tenant1Form.getVersionTag(), TENANT_1))
          .isEmpty();
      assertThat(
              formState.findFormByIdAndVersionTag(
                  tenant2Form.getFormId(), tenant2Form.getVersionTag(), TENANT_2))
          .isNotEmpty();
    }

    @Test
    void shouldNotFindVersion1ByIdAndDeploymentKeyAfterDeletingVersion1() {
      // given
      final var formV1 = sampleFormRecord();
      final var formV2 = sampleFormRecord(2L, "form-id", 2, 2L, TENANT_1);
      formCreatedApplier.applyState(formV1.getFormKey(), formV1);
      formCreatedApplier.applyState(formV2.getFormKey(), formV2);

      // when
      formDeletedApplier.applyState(formV1.getFormKey(), formV1);

      // then
      assertThat(
              formState.findFormByIdAndDeploymentKey(
                  formV1.getFormId(), formV1.getDeploymentKey(), formV1.getTenantId()))
          .isEmpty();
      assertThat(
              formState.findFormByIdAndDeploymentKey(
                  formV2.getFormId(), formV2.getDeploymentKey(), formV2.getTenantId()))
          .get()
          .extracting(PersistedForm::getFormKey, PersistedForm::getVersion)
          .containsExactly(2L, 2);
    }

    @Test
    void shouldNotFindVersion1ByIdAndVersionTagAfterDeletingVersion1() {
      // given
      final var formV1 = sampleFormRecord().setVersionTag("v1.0");
      final var formV2 = sampleFormRecord(2L, "form-id", 2, 2L, TENANT_1).setVersionTag("v2.0");
      formCreatedApplier.applyState(formV1.getFormKey(), formV1);
      formCreatedApplier.applyState(formV2.getFormKey(), formV2);

      // when
      formDeletedApplier.applyState(formV1.getFormKey(), formV1);

      // then
      assertThat(
              formState.findFormByIdAndVersionTag(
                  formV1.getFormId(), formV1.getVersionTag(), formV1.getTenantId()))
          .isEmpty();
      assertThat(
              formState.findFormByIdAndVersionTag(
                  formV2.getFormId(), formV2.getVersionTag(), formV2.getTenantId()))
          .get()
          .extracting(PersistedForm::getFormKey, PersistedForm::getVersion)
          .containsExactly(2L, 2);
    }

    @Test
    void shouldNotFindVersion2ByIdAndDeploymentKeyAfterDeletingVersion2() {
      // given
      final var formV1 = sampleFormRecord();
      final var formV2 = sampleFormRecord(2L, "form-id", 2, 2L, TENANT_1);
      formCreatedApplier.applyState(formV1.getFormKey(), formV1);
      formCreatedApplier.applyState(formV2.getFormKey(), formV2);

      // when
      formDeletedApplier.applyState(formV2.getFormKey(), formV2);

      // then
      assertThat(
              formState.findFormByIdAndDeploymentKey(
                  formV2.getFormId(), formV2.getDeploymentKey(), formV2.getTenantId()))
          .isEmpty();
      assertThat(
              formState.findFormByIdAndDeploymentKey(
                  formV1.getFormId(), formV1.getDeploymentKey(), formV1.getTenantId()))
          .get()
          .extracting(PersistedForm::getFormKey, PersistedForm::getVersion)
          .containsExactly(1L, 1);
    }

    @Test
    void shouldNotFindVersion2ByIdAndVersionTagAfterDeletingVersion2() {
      // given
      final var formV1 = sampleFormRecord().setVersionTag("v1.0");
      final var formV2 = sampleFormRecord(2L, "form-id", 2, 2L, TENANT_1).setVersionTag("v2.0");
      formCreatedApplier.applyState(formV1.getFormKey(), formV1);
      formCreatedApplier.applyState(formV2.getFormKey(), formV2);

      // when
      formDeletedApplier.applyState(formV2.getFormKey(), formV2);

      // then
      assertThat(
              formState.findFormByIdAndVersionTag(
                  formV2.getFormId(), formV2.getVersionTag(), formV2.getTenantId()))
          .isEmpty();
      assertThat(
              formState.findFormByIdAndVersionTag(
                  formV1.getFormId(), formV1.getVersionTag(), formV1.getTenantId()))
          .get()
          .extracting(PersistedForm::getFormKey, PersistedForm::getVersion)
          .containsExactly(1L, 1);
    }
  }
}
