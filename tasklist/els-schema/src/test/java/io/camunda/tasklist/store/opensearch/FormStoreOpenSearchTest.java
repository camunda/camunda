/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.store.opensearch;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.store.FormStore.FormIdView;
import io.camunda.tasklist.tenant.TenantAwareOpenSearchClient;
import io.camunda.webapps.schema.descriptors.index.FormIndex;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.tasklist.template.TaskTemplate;
import io.camunda.webapps.schema.entities.form.FormEntity;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.ErrorResponse;
import org.opensearch.client.opensearch._types.OpenSearchException;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.HitsMetadata;
import org.opensearch.client.opensearch.core.search.TotalHits;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FormStoreOpenSearchTest {
  @Mock private FormIndex formIndex = new FormIndex("test", false);

  @Mock private TaskTemplate taskTemplate = new TaskTemplate("test", false);

  @Mock private ProcessIndex processIndex = new ProcessIndex("test", false);

  @Mock private TenantAwareOpenSearchClient tenantAwareClient;

  @Mock private OpenSearchClient osClient;

  @InjectMocks private FormStoreOpenSearch instance;

  @Test
  void getFormWhenFormNotFound() throws IOException {
    when(formIndex.getIndexName()).thenReturn(FormIndex.INDEX_NAME);

    final var formSearchResponse = mock(SearchResponse.class);
    when(taskTemplate.getFullQualifiedName()).thenReturn("tasklist-task-x.0.0");
    when(processIndex.getFullQualifiedName()).thenReturn("tasklist-process-x.0.0");
    when(tenantAwareClient.search(
            (SearchRequest.Builder) any(SearchRequest.Builder.class), (Class<Object>) any()))
        .thenReturn(formSearchResponse);
    final var hitsMetadata = mock(HitsMetadata.class);
    when(formSearchResponse.hits()).thenReturn(hitsMetadata);
    final var totalHits = mock(TotalHits.class);
    when(hitsMetadata.total()).thenReturn(totalHits);
    when(totalHits.value()).thenReturn(0L);

    // when - then
    assertThatThrownBy(() -> instance.getForm("id1", "processDefId1", null))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("form with id id1 was not found");
  }

  @Test
  void getFormWhenIOExceptionOccurred() throws IOException {
    when(tenantAwareClient.search(any(SearchRequest.Builder.class), eq(FormEntity.class)))
        .thenThrow(new IOException("some IO exception"));

    // when - then
    assertThatThrownBy(() -> instance.getForm("id1", "processDefId1", null))
        .isInstanceOf(TasklistRuntimeException.class)
        .hasMessage("some IO exception")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void getFormByKeyNotExistShouldReturnEmpty() throws IOException {
    when(formIndex.getFullQualifiedName()).thenReturn(FormIndex.INDEX_NAME);

    when(osClient.get(any(GetRequest.class), eq(FormIdView.class)))
        .thenThrow(
            new OpenSearchException(
                new ErrorResponse.Builder()
                    .status(404)
                    .error(e -> e.reason("not found").type("not found"))
                    .build()));

    assertThat(instance.getFormByKey("id1")).isEmpty();
  }

  @Test
  void getForm() throws IOException {
    final var providedFormEntity =
        new FormEntity()
            .setId("id1")
            .setProcessDefinitionId("processDefId1")
            .setFormId("bpmnId1")
            .setSchema("");
    final var formSearchResponse = mock(SearchResponse.class);
    when(tenantAwareClient.search(any(SearchRequest.Builder.class), eq(FormEntity.class)))
        .thenReturn(formSearchResponse);
    final var hitsMetadata = mock(HitsMetadata.class);
    when(formSearchResponse.hits()).thenReturn(hitsMetadata);
    final var totalHits = mock(TotalHits.class);
    when(hitsMetadata.total()).thenReturn(totalHits);
    when(totalHits.value()).thenReturn(1L);
    final var hit = mock(Hit.class);
    when(hitsMetadata.hits()).thenReturn(List.of(hit));
    when(hit.source()).thenReturn(providedFormEntity);

    // when
    final var result = instance.getForm("id1", "processDefId1", null);

    // then
    assertThat(result).isEqualTo(providedFormEntity);
  }
}
