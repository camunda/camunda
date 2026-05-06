/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.resource;

import io.camunda.zeebe.engine.processing.ExcludeAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.deployment.PersistedResource;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.ResourceState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceReexportRecord;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceReexportIntent;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.concurrent.atomic.AtomicReference;

@ExcludeAuthorizationCheck
public class ResourceReexportReexportProcessor
    implements TypedRecordProcessor<ResourceReexportRecord> {

  private static final String RPA_FILE_EXTENSION = ".rpa";

  private final StateWriter stateWriter;
  private final TypedCommandWriter commandWriter;
  private final ResourceState resourceState;

  public ResourceReexportReexportProcessor(
      final Writers writers, final ProcessingState processingState) {
    stateWriter = writers.state();
    commandWriter = writers.command();
    resourceState = processingState.getResourceState();
  }

  @Override
  public void processRecord(final TypedRecord<ResourceReexportRecord> command) {
    final var value = command.getValue();
    final var foundResource = new AtomicReference<PersistedResource>();

    resourceState.visitResourcesByKey(
        value.getTenantId(),
        // We already reexported the key inside the command. We need to ensure we
        // continue with the next one.
        value.getResourceKey() + 1,
        resource -> {
          if (!BufferUtil.bufferAsString(resource.getResourceName()).endsWith(RPA_FILE_EXTENSION)) {
            return true; // We should only reexport RPA resources.
          }
          foundResource.set(resource.copy());
          return false; // stop the loop, we only want to reexport one resource at a time so we
          // don't impact the stream processor significantly.
        });

    if (foundResource.get() != null) {
      final var resource = foundResource.get();
      reexportResource(resource);
      commandWriter.appendFollowUpCommand(
          command.getKey(),
          ResourceReexportIntent.REEXPORT,
          new ResourceReexportRecord()
              .setResourceKey(resource.getResourceKey())
              .setTenantId(resource.getTenantId()));
    } else {
      stateWriter.appendFollowUpEvent(
          command.getKey(), ResourceReexportIntent.FINISHED, command.getValue());
    }
  }

  private void reexportResource(final PersistedResource resource) {
    final var resourceRecord =
        new ResourceRecord()
            .setResourceId(resource.getResourceId())
            .setVersion(resource.getVersion())
            .setResourceKey(resource.getResourceKey())
            .setChecksum(resource.getChecksum())
            .setResourceName(resource.getResourceName())
            .setTenantId(resource.getTenantId())
            .setDeploymentKey(resource.getDeploymentKey())
            .setVersionTag(resource.getVersionTag())
            .setResource(BufferUtil.wrapString(resource.getResource()));
    stateWriter.appendFollowUpEvent(
        resource.getResourceKey(), ResourceIntent.REEXPORTED, resourceRecord);
  }
}
