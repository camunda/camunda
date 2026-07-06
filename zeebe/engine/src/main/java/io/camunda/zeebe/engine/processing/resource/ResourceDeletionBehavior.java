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
import io.camunda.zeebe.engine.state.deployment.PersistedResource;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.resource.ResourceDeletionRecord;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.api.state.KeyGenerator;

final class ResourceDeletionBehavior {

  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final ResourceDeletionAuthorizationBehavior authorizationBehavior;

  ResourceDeletionBehavior(
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
      final PersistedResource resource) {
    command
        .getValue()
        .setResourceType(ResourceType.UNKNOWN)
        .setResourceId(resource.getResourceId())
        .setTenantId(resource.getTenantId());
    authorizationBehavior.authorize(
        command,
        PermissionType.DELETE_RESOURCE,
        bufferAsString(resource.getResourceId()),
        resource.getTenantId());
    stateWriter.appendFollowUpEvent(eventKey, intent, command.getValue());
    deleteResource(resource);
    return true;
  }

  private void deleteResource(final PersistedResource persistedResource) {
    final var resource =
        new ResourceRecord()
            .setResourceId(persistedResource.getResourceId())
            .setResourceKey(persistedResource.getResourceKey())
            .setTenantId(persistedResource.getTenantId())
            .setResourceName(persistedResource.getResourceName())
            .setResource(persistedResource.getResourceBuffer())
            .setChecksum(persistedResource.getChecksum())
            .setVersion(persistedResource.getVersion())
            .setVersionTag(persistedResource.getVersionTag())
            .setDeploymentKey(persistedResource.getDeploymentKey());
    stateWriter.appendFollowUpEvent(keyGenerator.nextKey(), ResourceIntent.DELETED, resource);
  }
}
