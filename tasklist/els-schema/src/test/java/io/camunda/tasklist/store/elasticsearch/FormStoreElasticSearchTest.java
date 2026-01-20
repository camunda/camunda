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

import co.elastic.clients.elasticsearch.ElasticsearchClient;
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
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
  @Mock private ElasticsearchClient es8Client;
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

    // Mock ES8 response
    final var es8Response = mock(SearchResponse.class);
    final var es8HitsMetadata = mock(HitsMetadata.class);
    final var es8TotalHits = mock(TotalHits.class);

    when(es8Client.search(any(SearchRequest.class), any(Class.class))).thenReturn(es8Response);
    when(es8Response.hits()).thenReturn(es8HitsMetadata);
    when(es8HitsMetadata.total()).thenReturn(es8TotalHits);
    when(es8TotalHits.value()).thenReturn(0L);

    // when - then
    assertThatThrownBy(() -> instance.getForm("id1", "processDefId1", null))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("form with id id1 was not found");
  }

  @Test
  void getFormWhenIOExceptionOccurred() throws IOException {
    // given - Mock ES8 client to throw IOException
    when(es8Client.search(any(SearchRequest.class), any(Class.class)))
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

    // Mock ES8 response
    final var es8Response = mock(SearchResponse.class);
    final var es8HitsMetadata = mock(HitsMetadata.class);
    final var es8TotalHits = mock(TotalHits.class);
    final var es8Hit = mock(Hit.class);

    when(es8Client.search(any(SearchRequest.class), any(Class.class))).thenReturn(es8Response);
    when(es8Response.hits()).thenReturn(es8HitsMetadata);
    when(es8HitsMetadata.total()).thenReturn(es8TotalHits);
    when(es8TotalHits.value()).thenReturn(1L);
    when(es8HitsMetadata.hits()).thenReturn(List.of(es8Hit));
    when(es8Hit.source()).thenReturn(providedFormEntity);

    // when
    final var result = instance.getForm("id3", "processDefId3", null);

    // then
    assertThat(result).isEqualTo(providedFormEntity);
  }
}
