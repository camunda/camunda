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
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.template.TaskTemplate;
import io.camunda.webapps.schema.entities.form.FormEntity;
import java.io.IOException;
import org.apache.lucene.search.TotalHits;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
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
  @Mock private TenantAwareElasticsearchClient tenantAwareClient;
  @Spy private ObjectMapper objectMapper = CommonUtils.OBJECT_MAPPER;
  @InjectMocks private FormStoreElasticSearch instance;

  @BeforeEach
  void setUp() {
    when(formIndex.getFullQualifiedName()).thenReturn(FORM_INDEX_NAME);
  }

  @Test
  void getFormWhenFormNotFound() throws IOException {
    // given
    when(formIndex.getIndexName()).thenReturn(FormIndex.INDEX_NAME);
    final var response = mock(SearchResponse.class);
    when(taskTemplate.getAlias()).thenReturn("tasklist-task-x.0.0");
    when(processIndex.getFullQualifiedName()).thenReturn("tasklist-task-x.0.0");
    when(tenantAwareClient.search(any(SearchRequest.class))).thenReturn(response);
    final var hits = mock(SearchHits.class);
    when(response.getHits()).thenReturn(hits);
    when(hits.getTotalHits()).thenReturn(new TotalHits(0L, TotalHits.Relation.EQUAL_TO));

    // when - then
    assertThatThrownBy(() -> instance.getForm("id1", "processDefId1", null))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("form with id id1 was not found");
  }

  @Test
  void getFormWhenIOExceptionOccurred() throws IOException {
    // given
    final var getForRequest = new GetRequest(FORM_INDEX_NAME).id("processDefId2_id2");
    when(tenantAwareClient.search(any(SearchRequest.class)))
        .thenThrow(new IOException("some error"));

    // when - then
    assertThatThrownBy(() -> instance.getForm("id2", "processDefId2", null))
        .isInstanceOf(TasklistRuntimeException.class)
        .hasMessage("some error")
        .hasCauseInstanceOf(IOException.class);
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

    final var responseAsstring = objectMapper.writeValueAsString(providedFormEntity);
    final var response = mock(SearchResponse.class);
    when(tenantAwareClient.search(any(SearchRequest.class))).thenReturn(response);
    final var hits = mock(SearchHits.class);
    when(response.getHits()).thenReturn(hits);
    when(hits.getTotalHits()).thenReturn(new TotalHits(1L, TotalHits.Relation.EQUAL_TO));
    final var hit = mock(SearchHit.class);
    when(hits.getHits()).thenReturn(new SearchHit[] {hit});
    when(hit.getSourceAsString()).thenReturn(responseAsstring);

    // when
    final var result = instance.getForm("id3", "processDefId3", null);

    // then
    assertThat(result).isEqualTo(providedFormEntity);
  }
}
