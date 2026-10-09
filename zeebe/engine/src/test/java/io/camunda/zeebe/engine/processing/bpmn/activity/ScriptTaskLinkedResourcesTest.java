/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior.LinkedResourceProps;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.LinkedResourceBuilder;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ResourceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.Resource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * Verifies that job worker script tasks resolve {@code zeebe:linkedResources} at job creation, the
 * same way service tasks do. This lets a script task link a script source (e.g. {@code sum.js})
 * that is deployed as a generic resource alongside the process; the worker reads the resolved
 * resource key from the {@code linkedResources} custom header.
 *
 * <p>The engine rule is shared, so every test uses its own process and resource ids.
 */
public final class ScriptTaskLinkedResourcesTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String JOB_TYPE = "io.camunda:managed-script:1";
  private static final String RESOURCE_TYPE = "ManagedScript";
  private static final String LINK_NAME = "script";
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateJobWithLinkedGenericResourceDeploymentBinding()
      throws JsonProcessingException {
    // given
    final var processId = "script-deployment-binding";
    final var scriptName = "sum-deployment.js";
    final var process =
        scriptTaskProcess(
            processId,
            l ->
                l.resourceId(scriptName)
                    .resourceType(RESOURCE_TYPE)
                    .bindingType(ZeebeBindingType.deployment)
                    .linkName(LINK_NAME));

    final var deployment =
        ENGINE
            .deployment()
            .withXmlResource(process)
            .withJsonResource(utf8("return a + b;"), scriptName)
            .deploy();

    // a newer version of the script must not be picked up: deployment binding pins the version
    // that was deployed together with the process
    ENGINE.deployment().withJsonResource(utf8("return a + b + 1;"), scriptName).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final var jobCreated = getJobCreated(processInstanceKey);
    assertThat(jobCreated.getValue().getType()).isEqualTo(JOB_TYPE);

    final var expectedResourceKey =
        deployment.getValue().getResourceMetadata().stream()
            .filter(metadata -> scriptName.equals(metadata.getResourceName()))
            .findFirst()
            .orElseThrow()
            .getResourceKey();
    assertThat(getLinkedResources(jobCreated))
        .extracting(
            LinkedResourceProps::getResourceKey,
            LinkedResourceProps::getResourceType,
            LinkedResourceProps::getLinkName)
        .containsExactly(tuple(String.valueOf(expectedResourceKey), RESOURCE_TYPE, LINK_NAME));
  }

  @Test
  public void shouldCreateJobWithLinkedGenericResourceLatestBinding()
      throws JsonProcessingException {
    // given
    final var processId = "script-latest-binding";
    final var scriptName = "calc-latest.py";
    final var process =
        scriptTaskProcess(
            processId,
            l ->
                l.resourceId(scriptName)
                    .resourceType(RESOURCE_TYPE)
                    .bindingType(ZeebeBindingType.latest)
                    .linkName(LINK_NAME));

    ENGINE
        .deployment()
        .withXmlResource(process)
        .withJsonResource(utf8("result = a * b"), scriptName)
        .deploy();
    ENGINE.deployment().withJsonResource(utf8("result = a * b * 2"), scriptName).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final List<Record<Resource>> resourceRecords =
        RecordingExporter.resourceRecords()
            .withResourceId(scriptName)
            .withIntent(ResourceIntent.CREATED)
            .limit(2)
            .toList();
    assertThat(resourceRecords).hasSize(2);

    assertThat(getLinkedResources(getJobCreated(processInstanceKey)))
        .extracting(
            LinkedResourceProps::getResourceKey,
            LinkedResourceProps::getResourceType,
            LinkedResourceProps::getLinkName)
        .containsExactly(
            tuple(String.valueOf(resourceRecords.getLast().getKey()), RESOURCE_TYPE, LINK_NAME));
  }

  @Test
  public void shouldCreateJobWithLinkedResourceVersionTagBinding() throws JsonProcessingException {
    // given - generic resources carry no version tag, so an RPA resource is used to cover the
    // version tag binding
    final var processId = "script-version-tag-binding";
    final var resourceId = "Rpa_script_task";
    final var process =
        scriptTaskProcess(
            processId,
            l ->
                l.resourceId(resourceId)
                    .resourceType(RESOURCE_TYPE)
                    .bindingType(ZeebeBindingType.versionTag)
                    .versionTag("1v")
                    .linkName(LINK_NAME));

    ENGINE
        .deployment()
        .withJsonResource(utf8(rpaResource(resourceId, "1v")), "script-task-1v.rpa")
        .deploy();
    ENGINE
        .deployment()
        .withJsonResource(utf8(rpaResource(resourceId, "2v")), "script-task-2v.rpa")
        .deploy();
    ENGINE.deployment().withXmlResource(process).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    final List<Record<Resource>> resourceRecords =
        RecordingExporter.resourceRecords()
            .withResourceId(resourceId)
            .withIntent(ResourceIntent.CREATED)
            .limit(2)
            .toList();
    assertThat(resourceRecords).hasSize(2);
    assertThat(resourceRecords.getFirst().getValue().getVersionTag()).isEqualTo("1v");

    assertThat(getLinkedResources(getJobCreated(processInstanceKey)))
        .extracting(LinkedResourceProps::getResourceKey)
        .containsExactly(String.valueOf(resourceRecords.getFirst().getKey()));
  }

  @Test
  public void shouldCreateIncidentIfGenericResourceHasNoVersionTag() {
    // given - generic resources are deployed without a version tag, so they cannot be resolved
    // through a version tag binding
    final var processId = "script-version-tag-generic";
    final var scriptName = "sum-version-tag.js";
    final var process =
        scriptTaskProcess(
            processId,
            l ->
                l.resourceId(scriptName)
                    .resourceType(RESOURCE_TYPE)
                    .bindingType(ZeebeBindingType.versionTag)
                    .versionTag("1v")
                    .linkName(LINK_NAME));

    ENGINE
        .deployment()
        .withXmlResource(process)
        .withJsonResource(utf8("return a + b;"), scriptName)
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(
            RecordingExporter.incidentRecords(IncidentIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .getFirst()
                .getValue())
        .extracting(IncidentRecordValue::getErrorType, IncidentRecordValue::getErrorMessage)
        .containsExactly(
            ErrorType.RESOURCE_NOT_FOUND,
            String.format(
                BpmnJobBehavior.FIND_RESOURCE_BY_ID_AND_VERSION_TAG_FAILED_MESSAGE,
                scriptName,
                "1v"));
  }

  @Test
  public void shouldCreateIncidentIfLinkedResourceNotFoundAndResolveItAfterDeployment() {
    // given
    final var processId = "script-not-found";
    final var scriptName = "sum-not-found.js";
    final var process =
        scriptTaskProcess(
            processId,
            l ->
                l.resourceId(scriptName)
                    .resourceType(RESOURCE_TYPE)
                    .bindingType(ZeebeBindingType.latest)
                    .linkName(LINK_NAME));
    ENGINE.deployment().withXmlResource(process).deploy();

    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    final var incident =
        RecordingExporter.incidentRecords(IncidentIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    assertThat(incident.getValue())
        .extracting(IncidentRecordValue::getErrorType, IncidentRecordValue::getErrorMessage)
        .containsExactly(
            ErrorType.RESOURCE_NOT_FOUND,
            String.format(BpmnJobBehavior.FIND_LATEST_RESOURCE_BY_ID_FAILED_MESSAGE, scriptName));

    // when - the missing script is deployed and the incident resolved
    ENGINE.deployment().withJsonResource(utf8("return a + b;"), scriptName).deploy();
    ENGINE.incident().ofInstance(processInstanceKey).withKey(incident.getKey()).resolve();

    // then
    assertThat(
            RecordingExporter.incidentRecords()
                .withProcessInstanceKey(processInstanceKey)
                .onlyEvents()
                .limit(2))
        .extracting(Record::getKey, Record::getIntent)
        .containsExactly(
            tuple(incident.getKey(), IncidentIntent.CREATED),
            tuple(incident.getKey(), IncidentIntent.RESOLVED));
    assertThat(getJobCreated(processInstanceKey).getValue().getCustomHeaders())
        .containsKey(Protocol.LINKED_RESOURCES_HEADER_NAME);
  }

  @Test
  public void shouldNotIncludeLinkedResourcesHeaderWithoutLinkedResources() {
    // given
    final var processId = "script-without-linked-resources";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .scriptTask("script", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(getJobCreated(processInstanceKey).getValue().getCustomHeaders())
        .doesNotContainKey(Protocol.LINKED_RESOURCES_HEADER_NAME);
  }

  @Test
  public void shouldIgnoreLinkedResourcesOfFeelScriptTask() {
    // given - a FEEL expression script task is not a job worker, so its linked resources are not
    // resolved and must not lead to an incident
    final var processId = "feel-script-with-linked-resources";
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .scriptTask(
                    "script",
                    t ->
                        t.zeebeExpression("1 + 2")
                            .zeebeResultVariable("result")
                            .zeebeLinkedResources(
                                l ->
                                    l.resourceId("not-deployed.js")
                                        .resourceType(RESOURCE_TYPE)
                                        .bindingType(ZeebeBindingType.latest)
                                        .linkName(LINK_NAME)))
                .endEvent()
                .done())
        .deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(processId).create();

    // then
    assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(BpmnElementType.PROCESS)
                .exists())
        .isTrue();
  }

  private static BpmnModelInstance scriptTaskProcess(
      final String processId, final Consumer<LinkedResourceBuilder> linkedResource) {
    return Bpmn.createExecutableProcess(processId)
        .startEvent()
        .scriptTask("script", t -> t.zeebeLinkedResources(linkedResource).zeebeJobType(JOB_TYPE))
        .endEvent()
        .done();
  }

  private static Record<JobRecordValue> getJobCreated(final long processInstanceKey) {
    return RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .getFirst();
  }

  private static List<LinkedResourceProps> getLinkedResources(final Record<JobRecordValue> job)
      throws JsonProcessingException {
    final var customHeaders = job.getValue().getCustomHeaders();
    assertThat(customHeaders).containsKey(Protocol.LINKED_RESOURCES_HEADER_NAME);
    return MAPPER.readValue(
        customHeaders.get(Protocol.LINKED_RESOURCES_HEADER_NAME), new TypeReference<>() {});
  }

  private static String rpaResource(final String id, final String versionTag) {
    return """
        {
          "type": "default",
          "id": "%s",
          "executionPlatform": "Camunda Cloud",
          "executionPlatformVersion": "8.7.0",
          "schemaVersion": 7,
          "versionTag": "%s",
          "resource": "Script content %s"
        }"""
        .formatted(id, versionTag, versionTag);
  }

  private static byte[] utf8(final String content) {
    return content.getBytes(StandardCharsets.UTF_8);
  }
}
