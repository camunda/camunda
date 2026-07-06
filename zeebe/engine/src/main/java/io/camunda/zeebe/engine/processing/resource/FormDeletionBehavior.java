/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.deployment.PersistedForm;
import io.camunda.zeebe.protocol.impl.record.value.deployment.FormRecord;
import io.camunda.zeebe.protocol.record.intent.FormIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

final class FormDeletionBehavior {

  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;

  FormDeletionBehavior(final StateWriter stateWriter, final KeyGenerator keyGenerator) {
    this.stateWriter = stateWriter;
    this.keyGenerator = keyGenerator;
  }

  void deleteForm(final PersistedForm persistedForm) {
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
