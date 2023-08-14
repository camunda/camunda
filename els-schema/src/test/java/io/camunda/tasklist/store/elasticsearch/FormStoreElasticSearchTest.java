/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store.elasticsearch;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.refEq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.tasklist.CommonUtils;
import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.FormIndex;
import java.io.IOException;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
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
  @Mock private RestHighLevelClient esClient;
  @Spy private ObjectMapper objectMapper = CommonUtils.OBJECT_MAPPER;
  @InjectMocks private FormStoreElasticSearch instance;

  @BeforeEach
  void setUp() {
    when(formIndex.getFullQualifiedName()).thenReturn(FORM_INDEX_NAME);
  }

  @Test
  void getFormWhenFormNotFound() throws IOException {
    // given
    final var getForRequest = new GetRequest(FORM_INDEX_NAME).id("processDefId1_id1");
    final var response = mock(GetResponse.class);
    when(response.isExists()).thenReturn(false);
    when(esClient.get(refEq(getForRequest), eq(RequestOptions.DEFAULT))).thenReturn(response);

    // when - then
    assertThatThrownBy(() -> instance.getForm("id1", "processDefId1"))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("No task form found with id id1");
  }

  @Test
  void getFormWhenIOExceptionOccurred() throws IOException {
    // given
    final var getForRequest = new GetRequest(FORM_INDEX_NAME).id("processDefId2_id2");
    when(esClient.get(refEq(getForRequest), eq(RequestOptions.DEFAULT)))
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
    final var getForRequest = new GetRequest(FORM_INDEX_NAME).id("processDefId3_id3");
    final var response = mock(GetResponse.class);
    when(response.isExists()).thenReturn(true);
    final var responseAsstring = objectMapper.writeValueAsString(providedFormEntity);
    when(response.getSourceAsString()).thenReturn(responseAsstring);
    when(esClient.get(refEq(getForRequest), eq(RequestOptions.DEFAULT))).thenReturn(response);

    // when
    final var result = instance.getForm("id3", "processDefId3");

    // then
    assertThat(result).isEqualTo(providedFormEntity);
  }
}
