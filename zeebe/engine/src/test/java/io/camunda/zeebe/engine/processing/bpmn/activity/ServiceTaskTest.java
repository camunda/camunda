/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior.LinkedResourceProps;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.mutable.MutableResourceState;
import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.protocol.impl.record.value.deployment.ResourceRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import org.camunda.bpm.model.xml.impl.util.ReflectUtil;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class ServiceTaskTest {
  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String RESOURCE_ID = "id";
  private static final String VERSION_TAG = "1v";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCreateJobWithLinkedResources() throws JsonProcessingException {
    final var record = createResourceRecord();
    final MutableResourceState resourceState =
        ((ProcessingDbState) ENGINE.getProcessingState()).getResourceState();
    resourceState.storeResourceInResourceColumnFamily(record);
    resourceState.storeResourceInResourceKeyByResourceIdAndVersionTagColumnFamily(record);

    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "my_linked_resource",
                t ->
                    t.zeebeLinkedResources(
                            l ->
                                l.resourceId(RESOURCE_ID)
                                    .resourceType("RPA")
                                    .bindingType(ZeebeBindingType.versionTag)
                                    .versionTag(VERSION_TAG)
                                    .linkName("my_link"))
                        .zeebeJobType("type"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(modelInstance).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final LinkedResourceProps resourceProps = new LinkedResourceProps();
    resourceProps.setResourceType("RPA");
    resourceProps.setLinkName("my_link");
    resourceProps.setResourceKey("1");

    Assertions.assertThat(jobCreated.getValue())
        .hasCustomHeaders(
            Map.of("linkedResources", MAPPER.writeValueAsString(List.of(resourceProps))));
  }

  @Test
  public void shouldHandleNotFound() {

    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask(
                "my_linked_resource",
                t ->
                    t.zeebeLinkedResources(
                            l ->
                                l.resourceId("2")
                                    .resourceType("RPA")
                                    .bindingType(ZeebeBindingType.versionTag)
                                    .versionTag(VERSION_TAG)
                                    .linkName("my_link"))
                        .zeebeJobType("type"))
            .endEvent()
            .done();

    ENGINE.deployment().withXmlResource(modelInstance).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    assertThat(
            RecordingExporter.jobRecords(JobIntent.CREATED)
                .withProcessInstanceKey(processInstanceKey)
                .exists())
        .isFalse();
  }

  private static ResourceRecord createResourceRecord() {
    final var record = new ResourceRecord();
    record.setResourceId(RESOURCE_ID);
    record.setVersionTag(VERSION_TAG);
    record.setResourceName("name");
    record.setVersion(1);
    record.setResourceKey(1L);
    return record;
  }

  @Test
  public void shouldNotIncludeLinkedResourcesWhenTheyAreNotInServiceTask() {
    final BpmnModelInstance modelInstance =
        Bpmn.readModelFromStream(
            ReflectUtil.getResourceAsStream("resource/test-send-task-with-linked-resources.bpmn"));

    ENGINE.deployment().withXmlResource(modelInstance).deploy();

    // when
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    final Record<JobRecordValue> jobCreated =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();

    assertThat(jobCreated.getValue().getCustomHeaders()).doesNotContainKey("linkedResources");
  }
}
