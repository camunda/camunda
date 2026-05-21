/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.exporter.common.cache.ExporterEntityCache;
import io.camunda.zeebe.exporter.common.cache.process.CachedProcessEntity;
import io.camunda.zeebe.exporter.common.extensionproperty.ExtensionPropertyConfiguration;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class ProcessCacheUtilTest {

  @Test
  void shouldNotRaiseExceptionWhenGetCallActivityIdHasOutOfBoundsIndex() {
    // given
    final Long fakeProcessDefinitionKey = 123L;
    final int outOfBoundsIndex = 10;
    final ExporterEntityCache<Long, CachedProcessEntity> mockProcessCache =
        mock(ExporterEntityCache.class);
    final CachedProcessEntity mockCachedProcessEntity = mock(CachedProcessEntity.class);
    final Optional<CachedProcessEntity> optionalCachedProcessEntity =
        Optional.of(mockCachedProcessEntity);
    final List<String> shortCallElementIds = List.of("callActivity_1", "callActivity_2");

    // with
    when(mockProcessCache.get(fakeProcessDefinitionKey)).thenReturn(optionalCachedProcessEntity);
    when(mockCachedProcessEntity.callElementIds()).thenReturn(shortCallElementIds);

    // when
    final Optional<String> result =
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
            () ->
                ProcessCacheUtil.getCallActivityId(
                    mockProcessCache, fakeProcessDefinitionKey, outOfBoundsIndex));

    // then
    assertThat(result).isNotNull();
    assertThat(result.isEmpty()).isTrue();
  }

  @Test
  void shouldExtractCallActivityIds() {
    // given
    final String processId = "testProcessId";
    final var model = buildModel(processId, List.of("C_Activity", "A_Activity", "D_Activity"));
    final var bpmnXml = Bpmn.convertToString(model);
    // when
    final var callActivities =
        ProcessCacheUtil.createCachedProcessEntity(
                null, 0, null, bpmnXml, processId, new ExtensionPropertyConfiguration())
            .callElementIds();
    // then
    assertThat(callActivities).containsExactly("A_Activity", "C_Activity", "D_Activity");
  }

  @Test
  void shouldOnlyRetainKnownToolPropertiesInProcessCache() {
    // given — a service task with both tool-related and unrelated zeebe:properties
    final String processId = "mixedPropsProcess";
    final var config = new ExtensionPropertyConfiguration();
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task1")
            .zeebeJobType("worker")
            .zeebeProperty(config.getToolNameProperty(), "myTool")
            .zeebeProperty("io.camunda.tool:description", "toolDesc")
            .zeebeProperty(config.getInboundConnectorTypeProperty(), "connector")
            .zeebeProperty("unrelated.property", "shouldBeFiltered")
            .zeebeProperty("another.unrelated", "alsoFiltered")
            .endEvent()
            .done();

    // when
    final var diagramData =
        ProcessCacheUtil.createCachedProcessEntity(
            null, 0, null, Bpmn.convertToString(model), processId, config);
    final var task1Props = diagramData.elementExtensionProperties().get("task1");

    // then — only tool-related keys are retained
    assertThat(task1Props)
        .containsOnlyKeys(
            config.getToolNameProperty(),
            "io.camunda.tool:description",
            config.getInboundConnectorTypeProperty());
  }

  @Test
  void shouldNotMatchAnyPropertyWhenPrefixIsNull() {
    // given — prefix set to null; only exact-match keys should be retained
    final String processId = "nullPrefixProcess";
    final var config = new ExtensionPropertyConfiguration();
    config.setToolPropertiesPrefix(null);
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task1")
            .zeebeJobType("worker")
            .zeebeProperty(config.getToolNameProperty(), "myTool")
            .zeebeProperty("io.camunda.tool:description", "wouldMatchDefaultPrefix")
            .zeebeProperty("unrelated.property", "filtered")
            .endEvent()
            .done();

    // when
    final var diagramData =
        ProcessCacheUtil.createCachedProcessEntity(
            null, 0, null, Bpmn.convertToString(model), processId, config);
    final var task1Props = diagramData.elementExtensionProperties().get("task1");

    // then — only the exact toolName key is retained; prefix-based match is skipped
    assertThat(task1Props).containsOnlyKeys(config.getToolNameProperty());
  }

  @Test
  void shouldNotMatchAnyPropertyWhenPrefixIsBlank() {
    // given — blank prefix would match every property name via startsWith(""), guard against it
    final String processId = "blankPrefixProcess";
    final var config = new ExtensionPropertyConfiguration();
    config.setToolPropertiesPrefix("  ");
    final BpmnModelInstance model =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task1")
            .zeebeJobType("worker")
            .zeebeProperty(config.getToolNameProperty(), "myTool")
            .zeebeProperty("unrelated.property", "filtered")
            .endEvent()
            .done();

    // when
    final var diagramData =
        ProcessCacheUtil.createCachedProcessEntity(
            null, 0, null, Bpmn.convertToString(model), processId, config);
    final var task1Props = diagramData.elementExtensionProperties().get("task1");

    // then — only the exact toolName key is retained; blank prefix does not match everything
    assertThat(task1Props).containsOnlyKeys(config.getToolNameProperty());
  }

  private BpmnModelInstance buildModel(final String processId, final List<String> callActivities) {
    final var builder = Bpmn.createExecutableProcess(processId).startEvent();
    callActivities.forEach(ca -> builder.callActivity(ca).zeebeProcessId(ca));
    return builder.done();
  }
}
