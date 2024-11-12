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
import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.test.util.Strings;
import org.junit.jupiter.api.Test;

public class FormCreatedV2ApplierTest extends AbstractFormCreatedApplierTest {

  @Test
  void shouldPutFormForDifferentTenants() {
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

    form1 =
        formState
            .findFormByIdAndDeploymentKey(wrapString(formId), deploymentKey, TENANT_1)
            .orElseThrow();
    form2 =
        formState
            .findFormByIdAndDeploymentKey(wrapString(formId), deploymentKey, TENANT_2)
            .orElseThrow();
    assertPersistedForm(form1, formKey, formId, version, deploymentKey, TENANT_1);
    assertPersistedForm(form2, formKey, formId, version, deploymentKey, TENANT_2);

    form1 =
        formState.findFormByIdAndVersionTag(wrapString(formId), versionTag, TENANT_1).orElseThrow();
    form2 =
        formState.findFormByIdAndVersionTag(wrapString(formId), versionTag, TENANT_2).orElseThrow();
    assertPersistedForm(form1, formKey, formId, version, deploymentKey, TENANT_1);
    assertPersistedForm(form2, formKey, formId, version, deploymentKey, TENANT_2);
  }

  @Test
  void shouldFindFormByFormIdAndDeploymentKey() {
    // given
    final var form1Version1 = sampleFormRecord(1L, "form-1", 1, 1L, TENANT_1);
    formCreatedApplier.applyState(form1Version1.getFormKey(), form1Version1);

    final var form2Version1 = sampleFormRecord(2L, "form-2", 1, 1L, TENANT_1);
    formCreatedApplier.applyState(form2Version1.getFormKey(), form2Version1);

    final var form1Version2 = sampleFormRecord(3L, "form-1", 2, 2L, TENANT_1);
    formCreatedApplier.applyState(form1Version2.getFormKey(), form1Version2);

    // when
    final var maybePersistedForm1 =
        formState.findFormByIdAndDeploymentKey(wrapString("form-1"), 1L, TENANT_1);
    final var maybePersistedForm2 =
        formState.findFormByIdAndDeploymentKey(wrapString("form-2"), 1L, TENANT_1);
    final var maybePersistedForm3 =
        formState.findFormByIdAndDeploymentKey(wrapString("form-1"), 2L, TENANT_1);
    final var maybePersistedForm4 =
        formState.findFormByIdAndDeploymentKey(wrapString("form-2"), 2L, TENANT_1);

    // then
    assertThat(maybePersistedForm1).hasValueSatisfying(isEqualToFormRecord(form1Version1));
    assertThat(maybePersistedForm2).hasValueSatisfying(isEqualToFormRecord(form2Version1));
    assertThat(maybePersistedForm3).hasValueSatisfying(isEqualToFormRecord(form1Version2));
    assertThat(maybePersistedForm4).isEmpty(); // form-2 not deployed again
  }

  @Test
  void shouldFindFormByFormIdAndVersionTag() {
    // given
    final var form1Version1 = sampleFormRecord(1L, "form-1", 1, 1L, TENANT_1).setVersionTag("v1.0");
    formCreatedApplier.applyState(form1Version1.getFormKey(), form1Version1);

    final var form2Version1 = sampleFormRecord(2L, "form-2", 1, 1L, TENANT_1).setVersionTag("v1.0");
    formCreatedApplier.applyState(form2Version1.getFormKey(), form2Version1);

    final var form1Version2 = sampleFormRecord(3L, "form-1", 2, 2L, TENANT_1).setVersionTag("v2.0");
    formCreatedApplier.applyState(form1Version2.getFormKey(), form1Version2);

    // when
    final var maybePersistedForm1 =
        formState.findFormByIdAndVersionTag(wrapString("form-1"), "v1.0", TENANT_1);
    final var maybePersistedForm2 =
        formState.findFormByIdAndVersionTag(wrapString("form-2"), "v1.0", TENANT_1);
    final var maybePersistedForm3 =
        formState.findFormByIdAndVersionTag(wrapString("form-1"), "v2.0", TENANT_1);
    final var maybePersistedForm4 =
        formState.findFormByIdAndVersionTag(wrapString("form-2"), "v2.0", TENANT_1);

    // then
    assertThat(maybePersistedForm1).hasValueSatisfying(isEqualToFormRecord(form1Version1));
    assertThat(maybePersistedForm2).hasValueSatisfying(isEqualToFormRecord(form2Version1));
    assertThat(maybePersistedForm3).hasValueSatisfying(isEqualToFormRecord(form1Version2));
    assertThat(maybePersistedForm4).isEmpty(); // form-2 not deployed again
  }

  @Test
  void shouldStoreFormKeyByIdAndVersionTagAndOverwriteExistingEntry() {
    // given
    final var formV1 = sampleFormRecord(1L, "form-id", 1, 1L, TENANT_1).setVersionTag("v1.0");
    final var formV1New = sampleFormRecord(2L, "form-id", 2, 2L, TENANT_1).setVersionTag("v1.0");
    formCreatedApplier.applyState(formV1.getFormKey(), formV1);

    // when
    formCreatedApplier.applyState(formV1New.getFormKey(), formV1New);

    // then
    assertThat(formState.findFormByIdAndVersionTag(wrapString("form-id"), "v1.0", TENANT_1))
        .hasValueSatisfying(isEqualToFormRecord(formV1New));
  }

  @Override
  TypedEventApplier<FormIntent, FormRecord> createEventApplier(final MutableFormState formState) {
    return new FormCreatedV2Applier(formState);
  }
}
