/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store.elasticsearch;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.FormIndex;
import io.camunda.tasklist.tenant.TenantAwareElasticsearchClient;
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

@ExtendWith(MockitoExtension.class)
class FormStoreElasticSearchTest {

  private static final String FORM_INDEX_NAME = "tasklist-form-x.0.0";
  @Mock private FormIndex formIndex = new FormIndex();
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
    when(tenantAwareClient.search(any(SearchRequest.class))).thenReturn(response);
    final var hits = mock(SearchHits.class);
    when(response.getHits()).thenReturn(hits);
    when(hits.getTotalHits()).thenReturn(new TotalHits(0L, TotalHits.Relation.EQUAL_TO));

    // when - then
    assertThatThrownBy(() -> instance.getForm("id1", "processDefId1"))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("form with id processDefId1_id1 was not found");
  }

  @Test
  void getFormWhenIOExceptionOccurred() throws IOException {
    // given
    final var getForRequest = new GetRequest(FORM_INDEX_NAME).id("processDefId2_id2");
    when(tenantAwareClient.search(any(SearchRequest.class)))
        .thenThrow(new IOException("some error"));

    // when - then
    assertThatThrownBy(() -> instance.getForm("id2", "processDefId2"))
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
            .setBpmnId("bpmnId3")
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
    final var result = instance.getForm("id3", "processDefId3");

    // then
    assertThat(result).isEqualTo(providedFormEntity);
  }
}
