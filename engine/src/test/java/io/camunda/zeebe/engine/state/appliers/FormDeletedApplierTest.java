/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;
import static org.assertj.core.api.Assertions.assertThat;

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
public class FormDeletedApplierTest {

  private static final String TENANT_1 = "tenant1";
  private static final String TENANT_2 = "tenant2";

  private final String tenantId = "<default>";
  private MutableProcessingState processingState;
  private MutableFormState formState;
  private FormCreatedApplier formCreatedApplier;
  private FormDeletedApplier formDeletedApplier;
  private KeyGenerator keyGenerator;

  @BeforeEach
  public void setup() {
    formState = processingState.getFormState();
    formCreatedApplier = new FormCreatedApplier(formState);
    formDeletedApplier = new FormDeletedApplier(formState);
    keyGenerator = processingState.getKeyGenerator();
  }

  @Test
  void shouldNotFindVersion2AsLatestFormAfterDeletion() {
    // given
    final var formV1 = sampleFormRecord();
    final var formV2 = sampleFormRecord(2L, "form-id", 2, TENANT_1);
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
  void shouldFindVersion2AsLatestFormAfterDeletion() {
    // given
    final var formV1 = sampleFormRecord();
    final var formV2 = sampleFormRecord(2L, "form-id", 2, TENANT_1);
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
    final var formV2 = sampleFormRecord(2L, "form-id", 2, TENANT_1);
    formCreatedApplier.applyState(formV2.getFormKey(), formV2);

    // when
    formDeletedApplier.applyState(form.getFormKey(), form);
    formDeletedApplier.applyState(formV2.getFormKey(), formV2);

    // then
    final var nextAvailableVersion =
        formState.getNextFormVersion(form.getFormId(), form.getTenantId());

    assertThat(nextAvailableVersion).isEqualTo(3);
  }

  @Test
  public void shouldDeleteFormForSpecificTenant() {
    // given
    final long formKey = keyGenerator.nextKey();
    final String formId = Strings.newRandomValidBpmnId();
    final int version = 1;
    final var tenant1Form = sampleFormRecord(formKey, formId, version, TENANT_1);
    final var tenant2Form = sampleFormRecord(formKey, formId, version, TENANT_2);

    formCreatedApplier.applyState(tenant1Form.getFormKey(), tenant1Form);
    formCreatedApplier.applyState(tenant2Form.getFormKey(), tenant2Form);

    // when
    formDeletedApplier.applyState(tenant1Form.getFormKey(), tenant1Form);

    // then
    assertThat(formState.findLatestFormById(tenant1Form.getFormId(), TENANT_1)).isEmpty();
    assertThat(formState.findLatestFormById(tenant2Form.getFormId(), TENANT_2)).isNotEmpty();
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
}
