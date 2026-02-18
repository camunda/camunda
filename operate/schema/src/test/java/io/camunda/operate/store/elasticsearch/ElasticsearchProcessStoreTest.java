/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.store.elasticsearch;

import static io.camunda.webapps.schema.descriptors.index.ProcessIndex.BPMN_XML;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.exceptions.OperateRuntimeException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.store.NotFoundException;
import io.camunda.operate.tenant.TenantAwareElasticsearchClient;
import io.camunda.webapps.schema.descriptors.ProcessInstanceDependant;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.template.ListViewTemplate;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceState;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ElasticsearchProcessStoreTest {

  @Mock private ProcessIndex processIndex;

  @Mock private ListViewTemplate listViewTemplate;

  private final List<ProcessInstanceDependant> processInstanceDependantTemplates =
      new LinkedList<>();

  @Mock private ObjectMapper objectMapper;

  @Mock private RestHighLevelClient esClient;

  @Mock private TenantAwareElasticsearchClient tenantAwareClient;

  @Mock private OperateProperties operateProperties;

  private ElasticsearchProcessStore underTest;

  @BeforeEach
  public void setup() {
    underTest =
        new ElasticsearchProcessStore(
            processIndex,
            listViewTemplate,
            processInstanceDependantTemplates,
            objectMapper,
            operateProperties,
            esClient,
            tenantAwareClient);
  }

  @Test
  public void testExceptionDuringGetDistinctCountFor() throws IOException {
    when(esClient.search(any(), any())).thenThrow(new IOException());
    when(processIndex.getAlias()).thenReturn("processIndexAlias");

    final Optional<Long> result = underTest.getDistinctCountFor("foo");

    assertThat(result).isNotNull();
    assertThat(result.isEmpty());
  }

  @Test
  public void testGetProcessByKeyTooManyResults() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");

    final SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    final SearchHits mockHits = Mockito.mock(SearchHits.class);
    // Normally TotalHits would just be mocked, but Mockito can't stub or mock direct field accesses
    final TotalHits.Relation mockRelation = Mockito.mock(TotalHits.Relation.class);
    final TotalHits mockTotalHits = new TotalHits(2L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> underTest.getProcessByKey(123L));
  }

  @Test
  public void testGetProcessByKeyNoResults() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");

    final SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    final SearchHits mockHits = Mockito.mock(SearchHits.class);
    // Normally TotalHits would just be mocked, but Mockito can't stub or mock direct field accesses
    final TotalHits.Relation mockRelation = Mockito.mock(TotalHits.Relation.class);
    final TotalHits mockTotalHits = new TotalHits(0L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> underTest.getProcessByKey(123L));
  }

  @Test
  public void testGetProcessByKeyWithException() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(() -> underTest.getProcessByKey(123L));
  }

  @Test
  public void testGetDiagramByKeyNoResults() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");

    final SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    final SearchHits mockHits = Mockito.mock(SearchHits.class);
    // Normally TotalHits would just be mocked, but Mockito can't stub or mock direct field accesses
    final TotalHits.Relation mockRelation = Mockito.mock(TotalHits.Relation.class);
    final TotalHits mockTotalHits = new TotalHits(0L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> underTest.getDiagramByKey(123L));
  }

  @Test
  public void testGetDiagramByKeyTooManyResults() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");

    final SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    final SearchHits mockHits = Mockito.mock(SearchHits.class);
    // Normally TotalHits would just be mocked, but Mockito can't stub or mock direct field accesses
    final TotalHits.Relation mockRelation = Mockito.mock(TotalHits.Relation.class);
    final TotalHits mockTotalHits = new TotalHits(2L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> underTest.getDiagramByKey(123L));
  }

  @Test
  public void testGetDiagramByKeyWithException() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(() -> underTest.getProcessByKey(123L));
  }

  @Test
  public void testExceptionDuringGetProcessesGrouped() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(() -> underTest.getProcessesGrouped("<default>", Set.of("demoProcess")));
  }

  @Test
  public void testExceptionDuringGetProcessesIdsToProcessesWithFields() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(
            () ->
                underTest.getProcessesIdsToProcessesWithFields(
                    Set.of("demoProcess", "demoProcess-1"), 10, "name", "bpmnProcessId", "key"));
  }

  @Test
  public void testGetProcessInstanceListViewByKeyTooManyResults() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");

    final SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    final SearchHits mockHits = Mockito.mock(SearchHits.class);
    // Normally TotalHits would just be mocked, but Mockito can't stub or mock direct field accesses
    final TotalHits.Relation mockRelation = Mockito.mock(TotalHits.Relation.class);
    final TotalHits mockTotalHits = new TotalHits(2L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> underTest.getProcessInstanceListViewByKey(123L));
  }

  @Test
  public void testGetProcessInstanceListViewByKeyNoResults() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");

    final SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    final SearchHits mockHits = Mockito.mock(SearchHits.class);
    // Normally TotalHits would just be mocked, but Mockito can't stub or mock direct field accesses
    final TotalHits.Relation mockRelation = Mockito.mock(TotalHits.Relation.class);
    final TotalHits mockTotalHits = new TotalHits(0L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> underTest.getProcessInstanceListViewByKey(123L));
  }

  @Test
  public void testGetProcessInstanceListViewByKeyWithException() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(() -> underTest.getProcessInstanceListViewByKey(123L));
  }

  @Test
  public void testExceptionDuringGetCoreStatistics() throws IOException {
    when(listViewTemplate.getFullQualifiedName()).thenReturn("listViewIndexPath");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(() -> underTest.getCoreStatistics(Set.of("demoProcess")));
  }

  @Test
  public void testGetProcessInstanceTreePathByIdNoResults() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");

    final SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    final SearchHits mockHits = Mockito.mock(SearchHits.class);
    // Normally TotalHits would just be mocked, but Mockito can't stub or mock direct field accesses
    final TotalHits.Relation mockRelation = Mockito.mock(TotalHits.Relation.class);
    final TotalHits mockTotalHits = new TotalHits(0L, mockRelation);

    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);

    assertThatExceptionOfType(NotFoundException.class)
        .isThrownBy(() -> underTest.getProcessInstanceTreePathById("PI_2251799813685251"));
  }

  @Test
  public void testExceptionDuringGetProcessInstanceTreePathById() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(() -> underTest.getProcessInstanceTreePathById("PI_2251799813685251"));
  }

  @Test
  public void testExceptionDuringDeleteProcessInstanceFromTreePath() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(() -> underTest.deleteProcessInstanceFromTreePath("2251799813685251"));
  }

  @Test
  public void testGetProcessInstancesByProcessAndStatesWithNullStates() {
    final Exception exception =
        assertThatExceptionOfType(OperateRuntimeException.class)
            .isThrownBy(() -> underTest.getProcessInstancesByProcessAndStates(123L, null, 10, null))
            .actual();
    assertThat(exception.getMessage())
        .isEqualTo("Parameter 'states' is needed to search by states.");
  }

  @Test
  public void testGetProcessInstancesByProcessAndStatesWithEmptyStates() {
    final Exception exception =
        assertThatExceptionOfType(OperateRuntimeException.class)
            .isThrownBy(
                () -> underTest.getProcessInstancesByProcessAndStates(123L, Set.of(), 10, null))
            .actual();
    assertThat(exception.getMessage())
        .isEqualTo("Parameter 'states' is needed to search by states.");
  }

  @Test
  public void testExceptionDuringGetProcessInstancesByProcessAndStates() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");
    when(tenantAwareClient.search(any())).thenThrow(new IOException());

    final Exception exception =
        assertThatExceptionOfType(OperateRuntimeException.class)
            .isThrownBy(
                () ->
                    underTest.getProcessInstancesByProcessAndStates(
                        123L, Set.of(ProcessInstanceState.COMPLETED), 10, null))
            .actual();
    assertThat(exception.getMessage())
        .contains("Failed to search process instances by processDefinitionKey");
  }

  @Test
  public void testGetProcessInstancesByParentKeysWithNullKeys() {
    final Exception exception =
        assertThatExceptionOfType(OperateRuntimeException.class)
            .isThrownBy(() -> underTest.getProcessInstancesByParentKeys(null, 10, null))
            .actual();
    assertThat(exception.getMessage())
        .isEqualTo("Parameter 'parentProcessInstanceKeys' is needed to search by parents.");
  }

  @Test
  public void testGetProcessInstancesByParentKeysWithEmptyKeys() {
    final Exception exception =
        assertThatExceptionOfType(OperateRuntimeException.class)
            .isThrownBy(() -> underTest.getProcessInstancesByParentKeys(Set.of(), 10, null))
            .actual();
    assertThat(exception.getMessage())
        .isEqualTo("Parameter 'parentProcessInstanceKeys' is needed to search by parents.");
  }

  @Test
  public void testExceptionDuringGetProcessInstancesByParentKeys() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");
    when(tenantAwareClient.search(any(), any())).thenThrow(new IOException());

    final Exception exception =
        assertThatExceptionOfType(OperateRuntimeException.class)
            .isThrownBy(() -> underTest.getProcessInstancesByParentKeys(Set.of(123L), 10, null))
            .actual();
    assertThat(exception.getMessage())
        .contains("Failed to search process instances by parentProcessInstanceKeys");
  }

  @Test
  public void testDeleteProcessInstancesAndDependantsWithNullKey() {
    final long deleted = underTest.deleteProcessInstancesAndDependants(null);
    assertThat(deleted).isEqualTo(0);
  }

  @Test
  public void testDeleteProcessInstancesAndDependantsWithEmptyKey() {
    final long deleted = underTest.deleteProcessInstancesAndDependants(Set.of());
    assertThat(deleted).isEqualTo(0);
  }

  @Test
  public void testExceptionDuringDeleteProcessInstancesAndDependants() throws IOException {
    when(listViewTemplate.getAlias()).thenReturn("listViewIndexAlias");
    when(esClient.deleteByQuery(any(), eq(RequestOptions.DEFAULT))).thenThrow(new IOException());

    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(() -> underTest.deleteProcessInstancesAndDependants(Set.of(123L)));
  }

  @Test
  public void testDeleteProcessDefinitionsByKeysWithNullKey() {
    final Long[] keys = null;
    final long deleted = underTest.deleteProcessDefinitionsByKeys(keys);
    assertThat(deleted).isEqualTo(0);
  }

  @Test
  public void testDeleteProcessDefinitionsByKeysWithEmptyKey() {
    final long deleted = underTest.deleteProcessDefinitionsByKeys(new Long[0]);
    assertThat(deleted).isEqualTo(0);
  }

  @Test
  public void testExceptionDuringDeleteProcessDefinitionsByKeys() throws IOException {
    when(processIndex.getAlias()).thenReturn("processIndexAlias");
    when(esClient.deleteByQuery(any(), eq(RequestOptions.DEFAULT))).thenThrow(new IOException());

    assertThatExceptionOfType(OperateRuntimeException.class)
        .isThrownBy(() -> underTest.deleteProcessDefinitionsByKeys(123L, 234L));
  }

  @Test
  public void testRefreshIndicesWithNullIndex() {
    final String[] indices = null;
    final Exception exception =
        assertThatExceptionOfType(OperateRuntimeException.class)
            .isThrownBy(() -> underTest.refreshIndices(indices))
            .actual();
    assertThat(exception.getMessage())
        .isEqualTo("Refresh indices needs at least one index to refresh.");
  }

  @Test
  public void testRefreshIndicesWithEmptyIndexArray() {
    final Exception exception =
        assertThatExceptionOfType(OperateRuntimeException.class)
            .isThrownBy(() -> underTest.refreshIndices(new String[0]))
            .actual();
    assertThat(exception.getMessage())
        .isEqualTo("Refresh indices needs at least one index to refresh.");
  }

  @Test
  public void testGetProcessByKeyExcludesBpmnXml() throws IOException {
    // Given - mock search response
    final SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    final SearchHits mockHits = Mockito.mock(SearchHits.class);
    final TotalHits mockTotalHits = new TotalHits(1L, Mockito.mock(TotalHits.Relation.class));
    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(mockHits.getHits()).thenReturn(new SearchHit[0]);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);
    when(processIndex.getAlias()).thenReturn("process-index");

    // When - getProcessByKey is called
    try {
      underTest.getProcessByKey(123L);
    } catch (final Exception e) {
      // Expected - mock doesn't return proper entity
    }

    // Then - capture the SearchRequest and verify BPMN_XML is excluded
    final ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    Mockito.verify(tenantAwareClient).search(captor.capture());

    final SearchRequest capturedRequest = captor.getValue();
    assertThat(capturedRequest).isNotNull();
    assertThat(capturedRequest.source()).isNotNull();
    assertThat(capturedRequest.source().fetchSource()).isNotNull();
    assertThat(capturedRequest.source().fetchSource().excludes()).contains(BPMN_XML);
  }

  @Test
  public void testGetDiagramByKeyIncludesBpmnXml() throws IOException {
    // Given - mock search response
    final SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    final SearchHits mockHits = Mockito.mock(SearchHits.class);
    final TotalHits mockTotalHits = new TotalHits(1L, Mockito.mock(TotalHits.Relation.class));
    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(mockHits.getHits()).thenReturn(new SearchHit[0]);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);
    when(processIndex.getAlias()).thenReturn("process-index");

    // When - getDiagramByKey is called
    try {
      underTest.getDiagramByKey(123L);
    } catch (final Exception e) {
      // Expected - mock doesn't return proper entity
    }

    // Then - capture the SearchRequest and verify BPMN_XML is NOT excluded (should be included)
    final ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    Mockito.verify(tenantAwareClient).search(captor.capture());

    final SearchRequest capturedRequest = captor.getValue();
    assertThat(capturedRequest).isNotNull();
    assertThat(capturedRequest.source()).isNotNull();
    // getDiagramByKey should include BPMN_XML, not exclude it
    final var fetchSource = capturedRequest.source().fetchSource();
    if (fetchSource != null && fetchSource.excludes() != null) {
      assertThat(fetchSource.excludes()).doesNotContain(BPMN_XML);
    }
    // Verify that BPMN_XML is in the includes (the method fetches BPMN_XML explicitly)
    if (fetchSource != null && fetchSource.includes() != null) {
      assertThat(fetchSource.includes()).contains(BPMN_XML);
    }
  }

  @Test
  public void testGetProcessByKeyExcludesOnlyBpmnXml() throws IOException {
    // Given - mock search response
    final SearchResponse mockResponse = Mockito.mock(SearchResponse.class);
    final SearchHits mockHits = Mockito.mock(SearchHits.class);
    final TotalHits mockTotalHits = new TotalHits(1L, Mockito.mock(TotalHits.Relation.class));
    when(mockResponse.getHits()).thenReturn(mockHits);
    when(mockHits.getTotalHits()).thenReturn(mockTotalHits);
    when(mockHits.getHits()).thenReturn(new SearchHit[0]);
    when(tenantAwareClient.search(any())).thenReturn(mockResponse);
    when(processIndex.getAlias()).thenReturn("process-index");

    // When - getProcessByKey is called
    try {
      underTest.getProcessByKey(456L);
    } catch (final Exception e) {
      // Expected - mock doesn't return proper entity
    }

    // Then - verify ONLY bpmnXml is in the excludes list
    final ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
    Mockito.verify(tenantAwareClient).search(captor.capture());

    final SearchRequest capturedRequest = captor.getValue();
    final String[] excludes = capturedRequest.source().fetchSource().excludes();
    assertThat(excludes).hasSize(1);
    assertThat(excludes).containsExactly(BPMN_XML);

    // Verify no includes are set (all other fields should be returned)
    assertThat(capturedRequest.source().fetchSource().includes()).isNullOrEmpty();
  }
}
