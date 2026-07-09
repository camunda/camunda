/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.deployment.PersistedForm;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

final class FormDeletionBehavior {

  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final ResourceDeletionAuthorizationBehavior authorizationBehavior;

  FormDeletionBehavior(
      final StateWriter stateWriter,
      final KeyGenerator keyGenerator,
      final ResourceDeletionAuthorizationBehavior authorizationBehavior) {
    this.stateWriter = stateWriter;
    this.keyGenerator = keyGenerator;
    this.authorizationBehavior = authorizationBehavior;
  }

  boolean tryDelete(
      final TypedRecord<ResourceDeletionRecord> command,
      final long eventKey,
      final Intent intent,
      final PersistedForm form) {
    command
        .getValue()
        .setResourceType(ResourceType.FORM)
        .setResourceId(form.getFormId())
        .setTenantId(form.getTenantId());
    authorizationBehavior.authorize(
        command, PermissionType.DELETE_FORM, bufferAsString(form.getFormId()), form.getTenantId());
    stateWriter.appendFollowUpEvent(eventKey, intent, command.getValue());
    deleteForm(form);
    return true;
  }

  private void deleteForm(final PersistedForm persistedForm) {
    final var form =
        new FormRecord()
            .setFormId(persistedForm.getFormId())
            .setFormKey(persistedForm.getFormKey())
            .setTenantId(persistedForm.getTenantId())
            .setResourceName(persistedForm.getResourceName())
            .setResource(persistedForm.getResource())
            .setChecksum(persistedForm.getChecksum())
            .setVersion(persistedForm.getVersion())
            .setVersionTag(persistedForm.getVersionTag())
            .setDeploymentKey(persistedForm.getDeploymentKey());

    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), FormIntent.DELETED, form);
  }
}
