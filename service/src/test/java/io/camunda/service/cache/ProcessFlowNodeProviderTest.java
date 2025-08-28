/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.cache.ProcessDefinitionProvider.ProcessCacheData;
import io.camunda.service.cache.ProcessDefinitionProvider.ProcessElement;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessDefinitionProviderTest {

  public static final String PROC_DEF_ID1 = "testProcess";
  public static final String PROC_DEF_ID2 = "parent_process_v1";
  public static final String PROC_DEF_ID3 = "eventSubprocessProcess";
  public static final String PROC_DEF_ID41 = "Process_0diikxu";
  public static final String PROC_DEF_ID42 = "Process_18z2cdf";
  private static final Long PROC_DEF_KEY = 1L;

  @InjectMocks ProcessDefinitionProvider processDefinitionProvider;

  @Mock(lenient = true)
  ProcessDefinitionServices processDefinitionServices;

  @Mock(lenient = true)
  ProcessDefinitionEntity processDefinition;

  @Mock BiConsumer<Long, ProcessElement> mockConsumer;
  private String bpmn1;
  private String bpmn2;
  private String bpmn3;
  private String bpmn4;

  String loadBpmn(final String name) throws IOException {
    try (final InputStream inputStream = getClass().getClassLoader().getResourceAsStream(name)) {
      assert inputStream != null;
      return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @BeforeEach
  void setupServices() throws Exception {
    bpmn1 = loadBpmn("xmlUtil-test1.bpmn");
    bpmn2 = loadBpmn("xmlUtil-test2.bpmn");
    bpmn3 = loadBpmn("xmlUtil-test3.bpmn");
    bpmn4 = loadBpmn("xmlUtil-test4.bpmn");
    when(processDefinition.processDefinitionKey()).thenReturn(PROC_DEF_KEY);
    when(processDefinition.processDefinitionId()).thenReturn(PROC_DEF_ID1);
    when(processDefinitionServices.getByKey(eq(PROC_DEF_KEY))).thenReturn(processDefinition);
    when(processDefinitionServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(processDefinitionServices);
  }

  private void verifyElementsBpmn1(ProcessCacheData processCacheData) {
    assertThat(processCacheData.elementIdNameMap()).containsEntry("StartEvent_1", "Start");
    assertThat(processCacheData.elementIdNameMap()).containsEntry("Event_0692jdh", "End");
    assertThat(processCacheData.elementIdNameMap()).containsEntry("taskB", "Task B");
  }

  private void verifyElementsBpmn2(ProcessCacheData processCacheData) {
    assertThat(processCacheData.elementIdNameMap()).containsEntry("call_activity", "Call Activity");
    assertThat(processCacheData.elementIdNameMap()).containsEntry("taskX", "TaskX");
    assertThat(processCacheData.elementIdNameMap()).containsEntry("taskY", "TaskY");
  }

  private void verifyElementsBpmn3(ProcessCacheData processCacheData) {
    assertThat(processCacheData.elementIdNameMap())
        .containsEntry("parentProcessTask", "Parent process task");
    assertThat(processCacheData.elementIdNameMap())
        .containsEntry("subprocessTask", "Subprocess task");
    assertThat(processCacheData.elementIdNameMap())
        .containsEntry("SubProcess_006dg16", "Event Subprocess inside Subprocess");
    assertThat(processCacheData.elementIdNameMap())
        .containsEntry("taskInSubprocess", "Task in sub-subprocess");
    assertThat(processCacheData.elementIdNameMap())
        .containsEntry("StartEvent_0kpitfv", "Timer in sub-subprocess");
  }

  @Test
  void shouldNotThrowExceptionWithInvalidXML() {
    // given
    when(processDefinition.bpmnXml()).thenReturn("not-xml");

    // when
    final ProcessCacheData processCacheData =
        processDefinitionProvider.extractProcessData(PROC_DEF_KEY);

    // then
    assertThat(processCacheData.elementIdNameMap()).isEmpty();
  }

  @Test
  void shouldExtractElementNames() {
    // given
    when(processDefinition.bpmnXml()).thenReturn(bpmn1);

    // when
    final ProcessCacheData processCacheData =
        processDefinitionProvider.extractProcessData(PROC_DEF_KEY);

    // then
    verifyElementsBpmn1(processCacheData);
  }

  @Test
  void shouldExtractElementNamesWithCallActivity() {
    // given
    when(processDefinition.bpmnXml()).thenReturn(bpmn2);
    when(processDefinition.processDefinitionId()).thenReturn(PROC_DEF_ID2);

    // when
    final ProcessCacheData processCacheData =
        processDefinitionProvider.extractProcessData(PROC_DEF_KEY);

    // then
    verifyElementsBpmn2(processCacheData);
  }

  @Test
  void shouldExtractElementNamesWithSubProcess() {
    // given
    when(processDefinition.bpmnXml()).thenReturn(bpmn3);
    when(processDefinition.processDefinitionId()).thenReturn(PROC_DEF_ID3);

    // when
    final ProcessCacheData processCacheData =
        processDefinitionProvider.extractProcessData(PROC_DEF_KEY);

    // then
    verifyElementsBpmn3(processCacheData);
  }

  @Test
  void shouldExtractElementNamesWith2Processes1() {
    // given
    when(processDefinition.bpmnXml()).thenReturn(bpmn4);
    when(processDefinition.processDefinitionId()).thenReturn(PROC_DEF_ID41);

    // when
    final ProcessCacheData processCacheData =
        processDefinitionProvider.extractProcessData(PROC_DEF_KEY);

    // then
    assertThat(processCacheData.elementIdNameMap())
        .containsEntry("StartEvent_1", "Start process A");
    assertThat(processCacheData.elementIdNameMap()).containsEntry("Activity_0whadek", "Do task A");
    assertThat(processCacheData.elementIdNameMap()).containsEntry("Event_0ovp9k6", "End process B");
  }

  @Test
  void shouldExtractElementNamesWith2Processes2() {
    // given
    when(processDefinition.bpmnXml()).thenReturn(bpmn4);
    when(processDefinition.processDefinitionId()).thenReturn(PROC_DEF_ID42);

    // when
    final ProcessCacheData processCacheData =
        processDefinitionProvider.extractProcessData(PROC_DEF_KEY);

    // then
    assertThat(processCacheData.elementIdNameMap())
        .containsEntry("Event_00cm7tu", "Start process B");
    assertThat(processCacheData.elementIdNameMap()).containsEntry("Activity_0w5vpxz", "Do task B");
    assertThat(processCacheData.elementIdNameMap()).containsEntry("Event_083hekc", "End process B");
  }

  @Test
  void shouldExtractElementNamesForMultipleProcessDefinitions() {
    // given
    when(processDefinition.bpmnXml()).thenReturn(bpmn1);
    when(processDefinition.processDefinitionKey()).thenReturn(PROC_DEF_KEY);
    final var processDefinition2 =
        new ProcessDefinitionEntity(2L, "Process 2", PROC_DEF_ID2, bpmn2, "", 1, "", "", "");
    final var processDefinition3 =
        new ProcessDefinitionEntity(3L, "Process 3", PROC_DEF_ID3, bpmn3, "", 1, "", "", "");
    when(processDefinitionServices.search(any()))
        .thenReturn(
            new SearchQueryResult.Builder<ProcessDefinitionEntity>()
                .items(List.of(processDefinition, processDefinition2, processDefinition3))
                .total(3)
                .build());

    // when
    final var processDataMap =
        processDefinitionProvider.extractProcessData(Set.of(PROC_DEF_KEY, 2L, 3L));

    // then
    assertThat(processDataMap).hasSize(3);

    final var processData1 = processDataMap.get(PROC_DEF_KEY);
    verifyElementsBpmn1(processData1);

    final var processData2 = processDataMap.get(2L);
    verifyElementsBpmn2(processData2);

    final var processData3 = processDataMap.get(3L);
    verifyElementsBpmn3(processData3);

    final var searchRequestCaptor = ArgumentCaptor.forClass(ProcessDefinitionQuery.class);
    verify(processDefinitionServices).search(searchRequestCaptor.capture());
    final var actualQuery = searchRequestCaptor.getValue();
    assertThat(actualQuery.filter().processDefinitionKeys()).hasSize(3);
    assertThat(actualQuery.filter().processDefinitionKeys()).containsOnly(PROC_DEF_KEY, 2L, 3L);
    assertThat(actualQuery.page()).isEqualTo(new SearchQueryPage.Builder().size(3).build());
  }
}
