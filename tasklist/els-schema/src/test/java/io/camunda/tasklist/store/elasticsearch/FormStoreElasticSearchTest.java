/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.elasticsearch;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.core.search.HitsMetadata;
import co.elastic.clients.elasticsearch.core.search.TotalHits;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.util.ElasticsearchTenantHelper;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.form.FormEntity;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FormStoreElasticSearchTest {

  private static final String FORM_INDEX_NAME = "tasklist-form-x.0.0";
  @Mock private FormIndex formIndex = new FormIndex("test", true);
  @Mock private TaskTemplate taskTemplate = new TaskTemplate("test", true);
  @Mock private ProcessIndex processIndex = new ProcessIndex("test", true);
  @Mock private ElasticsearchTenantHelper tenantHelper;
  @Mock private ElasticsearchClient esClient;
  @InjectMocks private FormStoreElasticSearch instance;

  @BeforeEach
  void setUp() {
    when(formIndex.getFullQualifiedName()).thenReturn(FORM_INDEX_NAME);
    when(tenantHelper.makeQueryTenantAware(any()))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void getFormWhenFormNotFound() throws IOException {
    // given
    when(formIndex.getIndexName()).thenReturn(FormIndex.INDEX_NAME);

    // Mock search response for embedded form lookup
    final var searchResponse = mock(SearchResponse.class);
    final var hitsMetadata = mock(HitsMetadata.class);
    final var totalHits = mock(TotalHits.class);

    when(esClient.search(any(SearchRequest.class), any(Class.class))).thenReturn(searchResponse);
    when(searchResponse.hits()).thenReturn(hitsMetadata);
    when(hitsMetadata.total()).thenReturn(totalHits);
    when(totalHits.value()).thenReturn(0L);

    // Mock ES count response for isFormAssociatedToTask and isFormAssociatedToProcess
    final var countResponse = mock(CountResponse.class);
    when(esClient.count(any(CountRequest.class))).thenReturn(countResponse);
    when(countResponse.count()).thenReturn(0L);

    // when - then
    assertThatThrownBy(() -> instance.getForm("id1", "processDefId1", null))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("form with id id1 was not found");
  }

  @Test
  void getFormWhenIOExceptionOccurred() throws IOException {
    // given - Mock client to throw IOException
    when(esClient.search(any(SearchRequest.class), any(Class.class)))
        .thenThrow(new IOException("some error"));
    when(esClient.count(any(CountRequest.class))).thenThrow(new IOException("some error"));

    // when - then
    assertThatThrownBy(() -> instance.getForm("id2", "processDefId2", null))
        .isInstanceOf(TasklistRuntimeException.class)
        .hasMessage("some error")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void getLinkedFormQueriesTaskAliasToIncludeArchivedIndices() throws IOException {
    final String taskAlias = "task-alias";
    when(taskTemplate.getAlias()).thenReturn(taskAlias);
    when(tenantHelper.makeQueryTenantAware(any(), any()))
        .thenAnswer(invocation -> invocation.getArgument(0));

    final var taskTotalHits = mock(TotalHits.class);
    when(taskTotalHits.value()).thenReturn(1L);
    final var taskHit = mock(Hit.class);
    when(taskHit.source()).thenReturn(Map.of(TaskTemplate.TENANT_ID, "tenant1"));
    final var taskHitsMetadata = mock(HitsMetadata.class);
    when(taskHitsMetadata.total()).thenReturn(taskTotalHits);
    when(taskHitsMetadata.hits()).thenReturn(List.of(taskHit));
    final var taskSearchResponse = mock(SearchResponse.class);
    when(taskSearchResponse.hits()).thenReturn(taskHitsMetadata);

    final Map<String, Object> formSource = new HashMap<>();
    formSource.put(FormIndex.BPMN_ID, "formBpmnId");
    formSource.put(FormIndex.VERSION, 1);
    formSource.put(FormIndex.EMBEDDED, false);
    formSource.put(FormIndex.SCHEMA, "{}");
    formSource.put(FormIndex.TENANT_ID, "tenant1");
    formSource.put(FormIndex.IS_DELETED, false);
    final var formHit = mock(Hit.class);
    when(formHit.source()).thenReturn(formSource);
    final var formHitsMetadata = mock(HitsMetadata.class);
    when(formHitsMetadata.hits()).thenReturn(List.of(formHit));
    final var formSearchResponse = mock(SearchResponse.class);
    when(formSearchResponse.hits()).thenReturn(formHitsMetadata);

    when(esClient.search(any(SearchRequest.class), any(Class.class)))
        .thenReturn(taskSearchResponse)
        .thenReturn(formSearchResponse);

    instance.getForm("formId", "procDefId", 1L);

    final var captor = ArgumentCaptor.forClass(SearchRequest.class);
    verify(esClient, times(2)).search(captor.capture(), any(Class.class));
    assertThat(captor.getAllValues().getFirst().index()).asList().contains(taskAlias);
  }

  @Test
  void getForm() throws IOException {
    // given
    final var providedFormEntity =
        new FormEntity()
            .setId("id3")
            .setProcessDefinitionId("processDefId3")
            .setFormId("bpmnId3")
            .setSchema("");

    // Mock response
    final var searchResponse = mock(SearchResponse.class);
    final var hitsMetadata = mock(HitsMetadata.class);
    final var totalHits = mock(TotalHits.class);
    final var hit = mock(Hit.class);

    when(esClient.search(any(SearchRequest.class), any(Class.class))).thenReturn(searchResponse);
    when(searchResponse.hits()).thenReturn(hitsMetadata);
    when(hitsMetadata.total()).thenReturn(totalHits);
    when(totalHits.value()).thenReturn(1L);
    when(hitsMetadata.hits()).thenReturn(List.of(hit));
    when(hit.source()).thenReturn(providedFormEntity);

    // when
    final var result = instance.getForm("id3", "processDefId3", null);

    // then
    assertThat(result).isEqualTo(providedFormEntity);
  }
}
