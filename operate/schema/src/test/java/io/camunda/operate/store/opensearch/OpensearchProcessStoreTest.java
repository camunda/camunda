/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.opensearch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.store.opensearch.client.sync.OpenSearchDocumentOperations;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.core.SearchRequest;

@ExtendWith(MockitoExtension.class)
public class OpensearchProcessStoreTest {

  @Mock private ProcessIndex processIndex;

  @Mock private RichOpenSearchClient richOpenSearchClient;

  @Mock private OpenSearchDocumentOperations documentOperations;

  @InjectMocks private OpensearchProcessStore underTest;

  @BeforeEach
  public void setup() {

    when(richOpenSearchClient.doc()).thenReturn(documentOperations);
    when(processIndex.getAlias()).thenReturn("process-index");
  }

  @Test
  public void testGetProcessByKeyExcludesBpmnXml() {
    // Given - mock document operations
    final ProcessEntity mockProcess = new ProcessEntity();
    mockProcess.setKey(123L);
    mockProcess.setBpmnProcessId("test-process");

    when(documentOperations.searchUnique(
            any(SearchRequest.Builder.class), eq(ProcessEntity.class), any(String.class)))
        .thenReturn(mockProcess);

    // When - getProcessByKey is called
    final ProcessEntity result = underTest.getProcessByKey(123L);

    // Then - verify the result is returned
    assertThat(result).isNotNull();
    assertThat(result.getKey()).isEqualTo(123L);

    // Verify that searchUnique was called with a SearchRequest.Builder
    final ArgumentCaptor<SearchRequest.Builder> captor =
        ArgumentCaptor.forClass(SearchRequest.Builder.class);
    verify(documentOperations).searchUnique(captor.capture(), eq(ProcessEntity.class), eq("123"));

    // Verify the search request builder was configured
    final SearchRequest.Builder requestBuilder = captor.getValue();
    assertThat(requestBuilder).isNotNull();

    // Build the request to inspect it
    final SearchRequest builtRequest = requestBuilder.build();

    // Verify source filtering is applied (excludes bpmnXml)
    assertThat(builtRequest.source()).isNotNull();
    assertThat(builtRequest.source().filter()).isNotNull();
    assertThat(builtRequest.source().filter().excludes()).contains(ProcessIndex.BPMN_XML);
  }

  @Test
  public void testGetDiagramByKeyIncludesBpmnXml() {
    // Given - mock document operations
    final ProcessEntity mockProcess = new ProcessEntity();
    mockProcess.setKey(123L);
    mockProcess.setBpmnXml("<?xml version=\"1.0\"?><process></process>");

    when(documentOperations.searchUnique(
            any(SearchRequest.Builder.class), eq(ProcessEntity.class), any(String.class)))
        .thenReturn(mockProcess);

    // When - getDiagramByKey is called
    final String result = underTest.getDiagramByKey(123L);

    // Then - verify BPMN XML is returned
    assertThat(result).isNotNull();
    assertThat(result).contains("<?xml");

    // Verify searchUnique was called
    verify(documentOperations)
        .searchUnique(any(SearchRequest.Builder.class), eq(ProcessEntity.class), eq("123"));
  }

  @Test
  public void testGetProcessByKeyExcludesOnlyBpmnXmlNotOtherFields() {
    // Given
    final ProcessEntity mockProcess = new ProcessEntity();
    mockProcess.setKey(456L);
    mockProcess.setBpmnProcessId("another-process");
    mockProcess.setName("Another Process");
    mockProcess.setVersion(2);

    when(documentOperations.searchUnique(
            any(SearchRequest.Builder.class), eq(ProcessEntity.class), any(String.class)))
        .thenReturn(mockProcess);

    // When
    final ProcessEntity result = underTest.getProcessByKey(456L);

    // Then - verify all other fields are present except bpmnXml
    assertThat(result).isNotNull();
    assertThat(result.getKey()).isEqualTo(456L);
    assertThat(result.getBpmnProcessId()).isEqualTo("another-process");
    assertThat(result.getName()).isEqualTo("Another Process");
    assertThat(result.getVersion()).isEqualTo(2);

    // Verify the request excludes ONLY bpmnXml
    final ArgumentCaptor<SearchRequest.Builder> captor =
        ArgumentCaptor.forClass(SearchRequest.Builder.class);
    verify(documentOperations).searchUnique(captor.capture(), eq(ProcessEntity.class), eq("456"));

    final SearchRequest builtRequest = captor.getValue().build();
    assertThat(builtRequest.source()).isNotNull();
    assertThat(builtRequest.source().filter()).isNotNull();

    final List<String> excludes = builtRequest.source().filter().excludes();
    assertThat(excludes).hasSize(1);
    assertThat(excludes).containsExactly(ProcessIndex.BPMN_XML);

    // Verify no includes are set (all other fields should be returned)
    assertThat(builtRequest.source().filter().includes()).isNullOrEmpty();
  }
}
