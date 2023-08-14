/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.store.opensearch;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.entities.FormEntity;
import io.camunda.tasklist.exceptions.NotFoundException;
import io.camunda.tasklist.exceptions.TasklistRuntimeException;
import io.camunda.tasklist.schema.indices.FormIndex;
import java.io.IOException;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.GetRequest;
import org.opensearch.client.opensearch.core.GetResponse;
import org.opensearch.client.util.ObjectBuilder;

@ExtendWith(MockitoExtension.class)
class FormStoreOpenSearchTest {
  @Mock private FormIndex formIndex = new FormIndex();

  @Mock private OpenSearchClient osClient;

  @InjectMocks private FormStoreOpenSearch instance;

  @Test
  void getFormWhenFormNotFound() throws IOException {
    final GetResponse<FormEntity> formEntityResponse = mock(GetResponse.class);
    when(formEntityResponse.found()).thenReturn(false);
    when(osClient.get(
            (Function<GetRequest.Builder, ObjectBuilder<GetRequest>>) any(), eq(FormEntity.class)))
        .thenReturn(formEntityResponse);

    // when - then
    assertThatThrownBy(() -> instance.getForm("id1", "processDefId1"))
        .isInstanceOf(NotFoundException.class)
        .hasMessage("No task form found with id id1");
  }

  @Test
  void getFormWhenIOExceptionOccurred() throws IOException {
    when(osClient.get(
            (Function<GetRequest.Builder, ObjectBuilder<GetRequest>>) any(), eq(FormEntity.class)))
        .thenThrow(new IOException("some IO exception"));

    // when - then
    assertThatThrownBy(() -> instance.getForm("id1", "processDefId1"))
        .isInstanceOf(TasklistRuntimeException.class)
        .hasMessage("some IO exception")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void getForm() throws IOException {
    final GetResponse<FormEntity> formEntityResponse = mock(GetResponse.class);
    final var providedFormEntity =
        new FormEntity()
            .setId("id1")
            .setProcessDefinitionId("processDefId1")
            .setBpmnId("bpmnId1")
            .setSchema("");
    when(formEntityResponse.found()).thenReturn(true);
    when(formEntityResponse.source()).thenReturn(providedFormEntity);
    when(osClient.get(
            (Function<GetRequest.Builder, ObjectBuilder<GetRequest>>) any(), eq(FormEntity.class)))
        .thenReturn(formEntityResponse);

    // when
    final var result = instance.getForm("id1", "processDefId1");

    // then
    assertThat(result).isEqualTo(providedFormEntity);
  }
}
