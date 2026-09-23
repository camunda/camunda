/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment.transform;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableScriptTask;
import io.camunda.zeebe.engine.processing.deployment.model.element.JobWorkerProperties;
import io.camunda.zeebe.engine.processing.deployment.model.element.LinkedResource;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ProcessMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceMetadataRecord;
import io.camunda.zeebe.protocol.record.intent.ManagedScriptDefinitionIntent;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionStatus;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.agrona.DirectBuffer;

final class ManagedScriptDefinitionTransformer {

  static final String LANGUAGE_HEADER = "language";
  static final String RUNTIME_HEADER = "runtime";
  static final String MANAGED_SCRIPT_JOB_TYPE = "io.camunda:managed-script:1";
  private static final String MANAGED_SCRIPT_RESOURCE_TYPE = "ManagedScript";
  private static final String SCRIPT_LINK_NAME = "script";

  private final KeyGenerator keyGenerator;
  private final StateWriter stateWriter;

  ManagedScriptDefinitionTransformer(
      final KeyGenerator keyGenerator, final StateWriter stateWriter) {
    this.keyGenerator = keyGenerator;
    this.stateWriter = stateWriter;
  }

  void writeRecords(
      final DeploymentRecord deployment,
      final ExecutableProcess process,
      final ProcessMetadata processMetadata) {
    process.getFlowElements().stream()
        .map(ManagedScriptDefinitionTransformer::resolveScriptTask)
        .filter(Objects::nonNull)
        .forEach(
            scriptTask ->
                findManagedScriptLink(scriptTask)
                    .ifPresent(
                        link -> createDefinition(deployment, processMetadata, scriptTask, link)));
  }

  private void createDefinition(
      final DeploymentRecord deployment,
      final ProcessMetadata processMetadata,
      final ExecutableScriptTask scriptTask,
      final LinkedResource linkedResource) {
    final ResourceMetadataRecord resource =
        deployment.resourceMetadata().stream()
            .filter(metadata -> linkedResource.getResourceId().equals(metadata.getResourceId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Expected managed script resource '%s' to have deployment metadata"
                            .formatted(linkedResource.getResourceId())));
    final long definitionKey = keyGenerator.nextKey();
    final Map<String, String> taskHeaders =
        scriptTask.getJobWorkerProperties() == null
            ? Map.of()
            : scriptTask.getJobWorkerProperties().getTaskHeaders();
    validateManagedScriptConfiguration(scriptTask, taskHeaders);
    final var definition =
        deployment
            .managedScriptDefinitionsMetadata()
            .add()
            .setManagedScriptDefinitionKey(definitionKey)
            .setStatus(ManagedScriptDefinitionStatus.PENDING)
            .setRevision(0L)
            .setResourceKey(resource.getResourceKey())
            .setResourceName(resource.getResourceName())
            .setArtifactDigest(
                artifactDigest(
                    resource.getChecksumBuffer(),
                    taskHeaders.get(LANGUAGE_HEADER),
                    taskHeaders.get(RUNTIME_HEADER)))
            .setElementId(BufferUtil.bufferAsString(scriptTask.getId()))
            .setBpmnProcessId(processMetadata.getBpmnProcessId())
            .setProcessDefinitionKey(processMetadata.getKey())
            .setProcessDefinitionVersion(processMetadata.getVersion())
            .setProcessDefinitionVersionTag(processMetadata.getVersionTag())
            .setLanguage(taskHeaders.getOrDefault(LANGUAGE_HEADER, ""))
            .setRuntime(taskHeaders.getOrDefault(RUNTIME_HEADER, ""))
            .setTenantId(deployment.getTenantId());

    stateWriter.appendFollowUpEvent(
        definitionKey, ManagedScriptDefinitionIntent.CREATED, definition);
  }

  private static DirectBuffer artifactDigest(
      final DirectBuffer resourceChecksum, final String language, final String runtime) {
    try {
      final var digest = MessageDigest.getInstance("SHA-256");
      digest.update(BufferUtil.bufferAsArray(resourceChecksum));
      digest.update((byte) 0);
      digest.update(language.getBytes(StandardCharsets.UTF_8));
      digest.update((byte) 0);
      digest.update(runtime.getBytes(StandardCharsets.UTF_8));
      return BufferUtil.wrapArray(digest.digest());
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private static void validateManagedScriptConfiguration(
      final ExecutableScriptTask scriptTask, final Map<String, String> taskHeaders) {
    final var jobWorkerProperties = scriptTask.getJobWorkerProperties();
    final var jobType = jobWorkerProperties == null ? null : jobWorkerProperties.getType();
    if (jobType == null
        || !jobType.isStatic()
        || !MANAGED_SCRIPT_JOB_TYPE.equals(jobType.getExpression())) {
      throw new IllegalArgumentException(
          "Managed script task '%s' must use job type '%s'"
              .formatted(BufferUtil.bufferAsString(scriptTask.getId()), MANAGED_SCRIPT_JOB_TYPE));
    }
    requireHeader(scriptTask, taskHeaders, LANGUAGE_HEADER);
    requireHeader(scriptTask, taskHeaders, RUNTIME_HEADER);
  }

  private static void requireHeader(
      final ExecutableScriptTask scriptTask,
      final Map<String, String> taskHeaders,
      final String header) {
    if (taskHeaders.getOrDefault(header, "").isBlank()) {
      throw new IllegalArgumentException(
          "Managed script task '%s' must define a non-blank '%s' task header"
              .formatted(BufferUtil.bufferAsString(scriptTask.getId()), header));
    }
  }

  private static java.util.Optional<LinkedResource> findManagedScriptLink(
      final ExecutableScriptTask scriptTask) {
    return java.util.Optional.ofNullable(scriptTask.getJobWorkerProperties())
        .map(JobWorkerProperties::getLinkedResources)
        .stream()
        .flatMap(List::stream)
        .filter(link -> link.getBindingType() == ZeebeBindingType.deployment)
        .filter(link -> MANAGED_SCRIPT_RESOURCE_TYPE.equals(link.getResourceType()))
        .filter(link -> SCRIPT_LINK_NAME.equals(link.getLinkName()))
        .findFirst();
  }

  private static ExecutableScriptTask resolveScriptTask(final ExecutableFlowElement element) {
    final var activity =
        element instanceof final ExecutableMultiInstanceBody multiInstanceBody
            ? multiInstanceBody.getInnerActivity()
            : element;
    return activity instanceof final ExecutableScriptTask scriptTask ? scriptTask : null;
  }
}
