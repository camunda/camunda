/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.deployment;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.ScriptTaskBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.ManagedScriptDefinitionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.ManagedScriptDefinitionStatus;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.junit.Rule;
import org.junit.Test;

public final class ManagedScriptDefinitionDeploymentTest {

  private static final byte[] SCRIPT_SOURCE = "export default () => 42;".getBytes(UTF_8);
  private static final String SCRIPT_RESOURCE_NAME = "script.js";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreatePendingManagedScriptDefinition() {
    // given
    final var processId = "managed-script-process";
    final var elementId = "managed-script-task";

    // when
    final var deployment =
        engine
            .deployment()
            .withXmlResource(managedScriptProcess(processId, elementId))
            .withJsonResource(SCRIPT_SOURCE, SCRIPT_RESOURCE_NAME)
            .deploy();
    final var processMetadata = deployment.getValue().getProcessesMetadata().getFirst();
    final var resourceMetadata = deployment.getValue().getResourceMetadata().getFirst();

    // then
    final var record =
        RecordingExporter.managedScriptDefinitionRecords(ManagedScriptDefinitionIntent.CREATED)
            .withProcessDefinitionKey(processMetadata.getProcessDefinitionKey())
            .getFirst();
    final var value = record.getValue();

    assertThat(record.getKey()).isEqualTo(value.getManagedScriptDefinitionKey());
    assertThat(value.getStatus()).isEqualTo(ManagedScriptDefinitionStatus.PENDING);
    assertThat(value.getResourceKey()).isEqualTo(resourceMetadata.getResourceKey());
    assertThat(value.getResourceName()).isEqualTo(SCRIPT_RESOURCE_NAME);
    assertThat(value.getArtifactDigest())
        .containsExactly(artifactDigest(resourceMetadata.getChecksum()));
    assertThat(value.getElementId()).isEqualTo(elementId);
    assertThat(value.getBpmnProcessId()).isEqualTo(processId);
    assertThat(value.getProcessDefinitionKey())
        .isEqualTo(processMetadata.getProcessDefinitionKey());
    assertThat(value.getProcessDefinitionVersion()).isEqualTo(1);
    assertThat(value.getProcessDefinitionVersionTag()).isEmpty();
    assertThat(value.getLanguage()).isEqualTo("javascript");
    assertThat(value.getRuntime()).isEqualTo("nodejs22");
    assertThat(value.getTenantId()).isEqualTo(TenantOwned.DEFAULT_TENANT_IDENTIFIER);

    final var definitionAndDeployment =
        RecordingExporter.records()
            .onlyEvents()
            .filter(
                event ->
                    event.getValueType() == ValueType.MANAGED_SCRIPT_DEFINITION
                        || (event.getValueType() == ValueType.DEPLOYMENT
                            && event.getIntent() == DeploymentIntent.CREATED))
            .limit(2)
            .asList();
    assertThat(definitionAndDeployment)
        .extracting(Record::getValueType, Record::getIntent)
        .containsExactly(
            tuple(ValueType.MANAGED_SCRIPT_DEFINITION, ManagedScriptDefinitionIntent.CREATED),
            tuple(ValueType.DEPLOYMENT, DeploymentIntent.CREATED));
  }

  @Test
  public void shouldCreateDefinitionForEachManagedScriptTask() {
    // given
    final var process =
        Bpmn.createExecutableProcess("multiple-managed-scripts")
            .startEvent()
            .scriptTask("script-a", this::configureManagedScript)
            .scriptTask("script-b", this::configureManagedScript)
            .endEvent()
            .done();

    // when
    final var deployment =
        engine
            .deployment()
            .withXmlResource(process)
            .withJsonResource(SCRIPT_SOURCE, SCRIPT_RESOURCE_NAME)
            .deploy();
    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // then
    final var definitions =
        RecordingExporter.managedScriptDefinitionRecords(ManagedScriptDefinitionIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .limit(2)
            .toList();
    assertThat(definitions)
        .extracting(record -> record.getValue().getElementId())
        .containsExactlyInAnyOrder("script-a", "script-b");
    assertThat(definitions)
        .extracting(record -> record.getValue().getManagedScriptDefinitionKey())
        .doesNotHaveDuplicates();
  }

  @Test
  public void shouldIncludeRuntimeInArtifactDigest() {
    // given
    final var process =
        Bpmn.createExecutableProcess("runtime-specific-digest")
            .startEvent()
            .scriptTask("script-a", task -> configureManagedScript(task, "nodejs22"))
            .scriptTask("script-b", task -> configureManagedScript(task, "nodejs24"))
            .endEvent()
            .done();

    // when
    final var deployment =
        engine
            .deployment()
            .withXmlResource(process)
            .withJsonResource(SCRIPT_SOURCE, SCRIPT_RESOURCE_NAME)
            .deploy();
    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();

    // then
    final var definitions =
        RecordingExporter.managedScriptDefinitionRecords(ManagedScriptDefinitionIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .limit(2)
            .toList();
    assertThat(definitions.get(0).getValue().getArtifactDigest())
        .isNotEqualTo(definitions.get(1).getValue().getArtifactDigest());
  }

  @Test
  public void shouldRejectManagedScriptWhenLinkedResourceIsMissing() {
    // given
    final var process = managedScriptProcess("missing-script-process", "script-task");

    // when
    final var rejection = engine.deployment().withXmlResource(process).expectRejection().deploy();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .contains(
            "Expected to find resource with id 'script.js' in current deployment, but not found.");
  }

  @Test
  public void shouldNotCreateDefinitionForNonManagedLinkedResources() {
    // given
    final var processId = "non-managed-scripts";
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .scriptTask(
                "wrong-resource-type",
                task ->
                    task.zeebeJobType("managed-script")
                        .zeebeLinkedResources(
                            link ->
                                link.resourceId(SCRIPT_RESOURCE_NAME)
                                    .resourceType("Other")
                                    .linkName("script")
                                    .bindingType(ZeebeBindingType.deployment)))
            .scriptTask(
                "wrong-link-name",
                task ->
                    task.zeebeJobType("managed-script")
                        .zeebeLinkedResources(
                            link ->
                                link.resourceId(SCRIPT_RESOURCE_NAME)
                                    .resourceType("ManagedScript")
                                    .linkName("other")
                                    .bindingType(ZeebeBindingType.deployment)))
            .endEvent()
            .done();

    // when
    engine
        .deployment()
        .withXmlResource(process)
        .withJsonResource(SCRIPT_SOURCE, SCRIPT_RESOURCE_NAME)
        .deploy();

    // then
    assertThat(
            RecordingExporter.<Boolean>expectNoMatchingRecords(
                records ->
                    RecordingExporter.managedScriptDefinitionRecords()
                        .withBpmnProcessId(processId)
                        .exists()))
        .isFalse();
  }

  @Test
  public void shouldRejectManagedScriptWithNonReservedJobType() {
    // given
    final var process =
        Bpmn.createExecutableProcess("wrong-job-type")
            .startEvent()
            .scriptTask(
                "script-task",
                task ->
                    task.zeebeJobType("custom-script-worker")
                        .zeebeTaskHeader("language", "javascript")
                        .zeebeTaskHeader("runtime", "nodejs22")
                        .zeebeLinkedResources(
                            link ->
                                link.resourceId(SCRIPT_RESOURCE_NAME)
                                    .resourceType("ManagedScript")
                                    .linkName("script")
                                    .bindingType(ZeebeBindingType.deployment)))
            .endEvent()
            .done();

    // when
    final var rejection =
        engine
            .deployment()
            .withXmlResource(process)
            .withJsonResource(SCRIPT_SOURCE, SCRIPT_RESOURCE_NAME)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .contains("must use job type 'io.camunda:managed-script:1'");
  }

  @Test
  public void shouldRejectManagedScriptWithoutRuntimeHeader() {
    // given
    final var process =
        Bpmn.createExecutableProcess("missing-runtime")
            .startEvent()
            .scriptTask(
                "script-task",
                task ->
                    task.zeebeJobType("io.camunda:managed-script:1")
                        .zeebeTaskHeader("language", "javascript")
                        .zeebeLinkedResources(
                            link ->
                                link.resourceId(SCRIPT_RESOURCE_NAME)
                                    .resourceType("ManagedScript")
                                    .linkName("script")
                                    .bindingType(ZeebeBindingType.deployment)))
            .endEvent()
            .done();

    // when
    final var rejection =
        engine
            .deployment()
            .withXmlResource(process)
            .withJsonResource(SCRIPT_SOURCE, SCRIPT_RESOURCE_NAME)
            .expectRejection()
            .deploy();

    // then
    Assertions.assertThat(rejection).hasRejectionType(RejectionType.INVALID_ARGUMENT);
    assertThat(rejection.getRejectionReason())
        .contains("must define a non-blank 'runtime' task header");
  }

  @Test
  public void shouldDeleteManagedScriptDefinitionWithProcessDefinition() {
    // given
    final var deployment =
        engine
            .deployment()
            .withXmlResource(managedScriptProcess("deleted-script-process", "script-task"))
            .withJsonResource(SCRIPT_SOURCE, SCRIPT_RESOURCE_NAME)
            .deploy();
    final long processDefinitionKey =
        deployment.getValue().getProcessesMetadata().getFirst().getProcessDefinitionKey();
    final var created =
        RecordingExporter.managedScriptDefinitionRecords(ManagedScriptDefinitionIntent.CREATED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst()
            .getValue();

    // when
    engine.resourceDeletion().withResourceKey(processDefinitionKey).delete();

    // then
    final var deleted =
        RecordingExporter.managedScriptDefinitionRecords(ManagedScriptDefinitionIntent.DELETED)
            .withProcessDefinitionKey(processDefinitionKey)
            .getFirst()
            .getValue();
    assertThat(deleted).usingRecursiveComparison().isEqualTo(created);

    final var definitionAndProcess =
        RecordingExporter.records()
            .onlyEvents()
            .filter(
                event ->
                    event.getIntent() == ManagedScriptDefinitionIntent.DELETED
                        || event.getIntent() == ProcessIntent.DELETED)
            .limit(2)
            .asList();
    assertThat(definitionAndProcess)
        .extracting(Record::getValueType, Record::getIntent)
        .containsExactly(
            tuple(ValueType.MANAGED_SCRIPT_DEFINITION, ManagedScriptDefinitionIntent.DELETED),
            tuple(ValueType.PROCESS, ProcessIntent.DELETED));
  }

  private BpmnModelInstance managedScriptProcess(final String processId, final String elementId) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .scriptTask(elementId, this::configureManagedScript)
        .endEvent()
        .done();
  }

  private void configureManagedScript(final ScriptTaskBuilder builder) {
    configureManagedScript(builder, "nodejs22");
  }

  private void configureManagedScript(final ScriptTaskBuilder builder, final String runtime) {
    builder
        .zeebeJobType("io.camunda:managed-script:1")
        .zeebeTaskHeader("language", "javascript")
        .zeebeTaskHeader("runtime", runtime)
        .zeebeLinkedResources(
            link ->
                link.resourceId(SCRIPT_RESOURCE_NAME)
                    .resourceType("ManagedScript")
                    .linkName("script")
                    .bindingType(ZeebeBindingType.deployment));
  }

  private static byte[] artifactDigest(final byte[] resourceChecksum) {
    try {
      final var digest = MessageDigest.getInstance("SHA-256");
      digest.update(resourceChecksum);
      digest.update((byte) 0);
      digest.update("javascript".getBytes(UTF_8));
      digest.update((byte) 0);
      digest.update("nodejs22".getBytes(UTF_8));
      return digest.digest();
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }
}
