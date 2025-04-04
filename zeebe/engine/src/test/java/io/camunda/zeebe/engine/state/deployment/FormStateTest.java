/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.engine.state.immutable.FormState.FormIdentifier;
import io.camunda.zeebe.engine.state.immutable.FormState.PersistedFormVisitor;
import io.camunda.zeebe.engine.state.mutable.MutableFormState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import java.util.function.LongConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

@ExtendWith(ProcessingStateExtension.class)
public class FormStateTest {

  private final String tenantId = "<default>";
  private MutableProcessingState processingState;
  private MutableFormState formState;

  @BeforeEach
  public void setup() {
    formState = processingState.getFormState();
  }

  @Test
  void shouldReturnEmptyIfNoFormIsDeployedForFormId() {
    // when
    final var persistedForm = formState.findLatestFormById("form-1", tenantId);

    // then
    assertThat(persistedForm).isEmpty();
  }

  @Test
  void shouldReturnEmptyIfNoFormIsDeployedForFormKey() {
    // when
    final var persistedForm = formState.findFormByKey(1L, tenantId);

    // then
    assertThat(persistedForm).isEmpty();
  }

  @Test
  void shouldReturnEmptyIfNoFormIsDeployedForFormIdAndDeploymentKey() {
    // when
    final var persistedForm = formState.findFormByIdAndDeploymentKey("form-1", 1L, tenantId);

    // then
    assertThat(persistedForm).isEmpty();
  }

  @Test
  void shouldReturnEmptyIfNoFormIsDeployedForFormIdAndVersionTag() {
    // when
    final var persistedForm = formState.findFormByIdAndVersionTag("form-1", "v1.0", tenantId);

    // then
    assertThat(persistedForm).isEmpty();
  }

  @Test
  void shouldIterateThroughAllForms() {
    // give -- three forms exist in the state
    final var form1 = createFormRecord(1);
    final var form2 = createFormRecord(2);
    final var form3 = createFormRecord(3);

    formState.storeFormInFormColumnFamily(form1);
    formState.storeFormInFormColumnFamily(form2);
    formState.storeFormInFormColumnFamily(form3);

    // when -- iterating through all forms
    final var visitor = Mockito.mock(LongConsumer.class);

    formState.forEachForm(
        null,
        (form) -> {
          visitor.accept(form.getFormKey());
          return true;
        });

    // then -- visited all three forms
    verify(visitor).accept(form1.getFormKey());
    verify(visitor).accept(form2.getFormKey());
    verify(visitor).accept(form3.getFormKey());
  }

  @Test
  void shouldSkipBeforeIteratingThroughAllForms() {
    // give -- three forms exist in the state
    final var form1 = createFormRecord(1);
    final var form2 = createFormRecord(2);
    final var form3 = createFormRecord(3);

    formState.storeFormInFormColumnFamily(form1);
    formState.storeFormInFormColumnFamily(form2);
    formState.storeFormInFormColumnFamily(form3);

    // when -- iterating through all forms
    final var visitor = Mockito.mock(PersistedFormVisitor.class);
    when(visitor.visit(any())).thenReturn(true);
    formState.forEachForm(new FormIdentifier(form1.getTenantId(), form1.getFormKey()), visitor);

    // then -- visited only the last form
    verify(visitor, times(2)).visit(any());
  }

  @Test
  public void shouldSetFormDeploymentKey() {
    // given
    final var noDeploymentKey = -1L;
    final var someDeploymentKey = 1L;
    final var form1 = createFormRecord(1).setDeploymentKey(noDeploymentKey);
    formState.storeFormInFormColumnFamily(form1);
    formState.storeFormInFormByIdAndVersionColumnFamily(form1);
    final var initialDeploymentKey =
        formState.findFormByKey(form1.getFormKey(), tenantId).get().getDeploymentKey();

    // when
    formState.setMissingDeploymentKey(tenantId, form1.getFormKey(), 1L);

    // then
    final var updatedDeploymentKey =
        formState.findFormByKey(form1.getFormKey(), tenantId).get().getDeploymentKey();

    assertThat(initialDeploymentKey).isEqualTo(noDeploymentKey);
    assertThat(updatedDeploymentKey).isEqualTo(someDeploymentKey);
  }

  @Test
  public void shouldSetFormDeploymentKeyPersistently() {
    // given
    final var noDeploymentKey = -1L;
    final var someDeploymentKey = 1L;
    final var form = createFormRecord(1).setDeploymentKey(noDeploymentKey);
    formState.storeFormInFormColumnFamily(form);
    formState.storeFormInFormByIdAndVersionColumnFamily(form);
    final var initialDeploymentKey =
        formState.findFormByKey(form.getFormKey(), tenantId).get().getDeploymentKey();
    formState.setMissingDeploymentKey(tenantId, form.getFormKey(), 1L);

    // when
    formState.clearCache();

    // then
    final var updatedDeploymentKey =
        formState.findFormByKey(form.getFormKey(), tenantId).get().getDeploymentKey();

    assertThat(initialDeploymentKey).isEqualTo(noDeploymentKey);
    assertThat(updatedDeploymentKey).isEqualTo(someDeploymentKey);
  }

  @Test
  public void shouldFindFormByDeploymentKeyAfterUpdating() {
    // given
    final var noDeploymentKey = -1L;
    final var someDeploymentKey = 1L;
    final var form = createFormRecord(1).setDeploymentKey(noDeploymentKey);
    formState.storeFormInFormColumnFamily(form);
    formState.storeFormInFormByIdAndVersionColumnFamily(form);
    final var initialLookupByDeploymentKey =
        formState.findFormByIdAndDeploymentKey(form.getFormId(), someDeploymentKey, tenantId);

    // when
    formState.setMissingDeploymentKey(tenantId, form.getFormKey(), 1L);

    // then
    final var lookupAfterUpdating =
        formState.findFormByIdAndDeploymentKey(form.getFormId(), someDeploymentKey, tenantId);

    assertThat(initialLookupByDeploymentKey).isEmpty();
    assertThat(lookupAfterUpdating).isPresent();
    assertThat(lookupAfterUpdating.get().getFormKey()).isEqualTo(form.getFormKey());
  }

  private FormRecord createFormRecord(final long key) {
    return new FormRecord()
        .setFormId("form")
        .setResourceName("form.form")
        .setVersion(1)
        .setFormKey(Protocol.encodePartitionId(1, key));
  }
}
