/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.gateway.rest.util.ProcessElementProvider.ProcessElement;
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
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {ProcessElementProvider.class})
class ProcessElementProviderTest {

  public static final String PROC_DEF_ID1 = "testProcess";
  public static final String PROC_DEF_ID2 = "parent_process_v1";
  public static final String PROC_DEF_ID3 = "eventSubprocessProcess";
  public static final String PROC_DEF_ID41 = "Process_0diikxu";
  public static final String PROC_DEF_ID42 = "Process_18z2cdf";
  private static final Long PROC_DEF_KEY = 1L;
  @Autowired ProcessElementProvider processElementProvider;
  @MockBean ProcessDefinitionServices processDefinitionServices;
  @Mock ProcessDefinitionEntity processDefinition;
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
  }

  private void verifyElementsBpmn1() {
    verify(mockConsumer)
        .accept(
            ProcessElementProviderTest.PROC_DEF_KEY, new ProcessElement("StartEvent_1", "Start"));
    verify(mockConsumer)
        .accept(
            ProcessElementProviderTest.PROC_DEF_KEY, new ProcessElement("Event_0692jdh", "End"));
    verify(mockConsumer)
        .accept(ProcessElementProviderTest.PROC_DEF_KEY, new ProcessElement("taskB", "Task B"));
  }

  private void verifyElementsBpmn2(final long key) {
    verify(mockConsumer).accept(key, new ProcessElement("call_activity", "Call Activity"));
    verify(mockConsumer).accept(key, new ProcessElement("taskX", "TaskX"));
    verify(mockConsumer).accept(key, new ProcessElement("taskY", "TaskY"));
  }

  private void verifyElementsBpmn3(final long key) {
    verify(mockConsumer)
        .accept(key, new ProcessElement("parentProcessTask", "Parent process task"));
    verify(mockConsumer).accept(key, new ProcessElement("subprocessTask", "Subprocess task"));
    verify(mockConsumer)
        .accept(
            key, new ProcessElement("SubProcess_006dg16", "Event Subprocess inside Subprocess"));
    verify(mockConsumer)
        .accept(key, new ProcessElement("taskInSubprocess", "Task in sub-subprocess"));
    verify(mockConsumer)
        .accept(key, new ProcessElement("StartEvent_0kpitfv", "Timer in sub-subprocess"));
  }

  @Test
  void shouldNotThrowExceptionWithInvalidXML() {
    // given
    when(processDefinition.bpmnXml()).thenReturn("not-xml");
    // when
    processElementProvider.extractElementNames(PROC_DEF_KEY, mockConsumer);
    // then
    verifyNoInteractions(mockConsumer);
  }

  @Test
  void shouldExtractElementNames() {
    // given
    when(processDefinition.bpmnXml()).thenReturn(bpmn1);
    // when
    processElementProvider.extractElementNames(PROC_DEF_KEY, mockConsumer);
    // then
    verifyElementsBpmn1();
    verifyNoMoreInteractions(mockConsumer);
  }

  @Test
  void shouldExtractElementNamesWithCallActivity() {
    // given
    when(processDefinition.bpmnXml()).thenReturn(bpmn2);
    when(processDefinition.processDefinitionId()).thenReturn(PROC_DEF_ID2);
    // when
    processElementProvider.extractElementNames(PROC_DEF_KEY, mockConsumer);
    // then
    verifyElementsBpmn2(PROC_DEF_KEY);
    verifyNoMoreInteractions(mockConsumer);
  }

  @Test
  void shouldExtractElementNamesWithSubProcess() {
    // given
    when(processDefinition.bpmnXml()).thenReturn(bpmn3);
    when(processDefinition.processDefinitionId()).thenReturn(PROC_DEF_ID3);
    // when
    processElementProvider.extractElementNames(PROC_DEF_KEY, mockConsumer);
    // then
    verifyElementsBpmn3(PROC_DEF_KEY);
    verifyNoMoreInteractions(mockConsumer);
  }

  @Test
  void shouldExtractElementNamesWith2Processes1() {
    // given
    when(processDefinition.bpmnXml()).thenReturn(bpmn4);
    when(processDefinition.processDefinitionId()).thenReturn(PROC_DEF_ID41);
    // when
    processElementProvider.extractElementNames(PROC_DEF_KEY, mockConsumer);
    // then
    verify(mockConsumer)
        .accept(PROC_DEF_KEY, new ProcessElement("StartEvent_1", "Start process A"));
    verify(mockConsumer).accept(PROC_DEF_KEY, new ProcessElement("Activity_0whadek", "Do task A"));
    verify(mockConsumer)
        .accept(PROC_DEF_KEY, new ProcessElement("Event_0ovp9k6", "End process B"));
    verifyNoMoreInteractions(mockConsumer);
  }

  @Test
  void shouldExtractElementNamesWith2Processes2() {
    // given
    when(processDefinition.bpmnXml()).thenReturn(bpmn4);
    when(processDefinition.processDefinitionId()).thenReturn(PROC_DEF_ID42);
    // when
    processElementProvider.extractElementNames(PROC_DEF_KEY, mockConsumer);
    // then
    verify(mockConsumer)
        .accept(PROC_DEF_KEY, new ProcessElement("Event_00cm7tu", "Start process B"));
    verify(mockConsumer).accept(PROC_DEF_KEY, new ProcessElement("Activity_0w5vpxz", "Do task B"));
    verify(mockConsumer)
        .accept(PROC_DEF_KEY, new ProcessElement("Event_083hekc", "End process B"));
    verifyNoMoreInteractions(mockConsumer);
  }

  @Test
  void shouldExtractElementNamesForMultipleProcessDefinitions() {
    // given
    when(processDefinition.bpmnXml()).thenReturn(bpmn1);
    when(processDefinition.processDefinitionKey()).thenReturn(PROC_DEF_KEY);
    final var processDefinition2 =
        new ProcessDefinitionEntity(2L, "", PROC_DEF_ID2, bpmn2, "", 1, "", "", "");
    final var processDefinition3 =
        new ProcessDefinitionEntity(3L, "", PROC_DEF_ID3, bpmn3, "", 1, "", "", "");
    when(processDefinitionServices.search(any()))
        .thenReturn(
            new SearchQueryResult.Builder<ProcessDefinitionEntity>()
                .items(List.of(processDefinition, processDefinition2, processDefinition3))
                .total(3)
                .build());
    // when
    processElementProvider.extractElementNames(Set.of(PROC_DEF_KEY, 2L, 3L), mockConsumer);
    // then
    verifyElementsBpmn1();
    verifyElementsBpmn2(2L);
    verifyElementsBpmn3(3L);
    verifyNoMoreInteractions(mockConsumer);

    final var searchRequestCaptor = ArgumentCaptor.forClass(ProcessDefinitionQuery.class);
    verify(processDefinitionServices).search(searchRequestCaptor.capture());
    final var actualQuery = searchRequestCaptor.getValue();
    assertThat(actualQuery.filter().processDefinitionKeys()).hasSize(3);
    assertThat(actualQuery.filter().processDefinitionKeys()).containsOnly(PROC_DEF_KEY, 2L, 3L);
    assertThat(actualQuery.page()).isEqualTo(new SearchQueryPage.Builder().size(3).build());
  }
}
