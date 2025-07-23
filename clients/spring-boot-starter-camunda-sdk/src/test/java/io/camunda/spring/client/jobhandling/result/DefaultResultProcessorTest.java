/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.spring.client.jobhandling.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.CreateDocumentBatchCommandStep1;
import io.camunda.client.api.command.CreateDocumentBatchCommandStep1.CreateDocumentBatchCommandStep2;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.response.DocumentReferenceBatchResponse;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.spring.client.jobhandling.DocumentContext;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultResultProcessorTest {

  @Mock CamundaClient camundaClient;

  @Mock
  DocumentResultProcessorFailureHandlingStrategy documentResultProcessorFailureHandlingStrategy;

  private DefaultResultProcessor defaultResultProcessor;

  @BeforeEach
  void setUp() {
    defaultResultProcessor =
        new DefaultResultProcessor(camundaClient, documentResultProcessorFailureHandlingStrategy);
  }

  @Test
  public void testProcessMethodShouldReturnResult() {
    // Given
    final String inputValue = "input";
    final ActivatedJob job = mock(ActivatedJob.class);
    final ResultProcessorContext context = new ResultProcessorContext(inputValue, job);
    // When
    final Object resultValue = defaultResultProcessor.process(context);
    // Then
    assertThat(resultValue).isEqualTo(inputValue);
  }

  @Test
  void shouldProcessDocumentContext() {
    final ResultContainingDocument inputValue =
        new ResultContainingDocument(
            DocumentContext.result()
                .addDocument("someDoc.txt", b -> b.content("Hello World!"))
                .build());
    final ActivatedJob job = mock(ActivatedJob.class);
    final ResultProcessorContext context = new ResultProcessorContext(inputValue, job);
    final CreateDocumentBatchCommandStep1 step1 = mock(CreateDocumentBatchCommandStep1.class);
    when(camundaClient.newCreateDocumentBatchCommand()).thenReturn(step1);
    final CreateDocumentBatchCommandStep2 step2 = mock(CreateDocumentBatchCommandStep2.class);
    when(step1.addDocument()).thenReturn(step2);
    when(step2.content(anyString())).thenReturn(step2);
    when(step2.done()).thenReturn(step1);
    final DocumentReferenceBatchResponse batchResponse = mock(DocumentReferenceBatchResponse.class);
    when(step1.execute()).thenReturn(batchResponse);
    when(batchResponse.isSuccessful()).thenReturn(true);
    final DocumentReferenceResponse documentReference = mock(DocumentReferenceResponse.class);
    when(batchResponse.getCreatedDocuments()).thenReturn(List.of(documentReference));

    final Object result = defaultResultProcessor.process(context);
    assertThat(result).isInstanceOf(ResultContainingDocument.class);
    final ResultContainingDocument resultContainingDocument = (ResultContainingDocument) result;
    assertThat(resultContainingDocument.context()).isInstanceOf(ResultDocumentContext.class);
    final ResultDocumentContext responseDocumentContext =
        (ResultDocumentContext) resultContainingDocument.context();
    assertThat(responseDocumentContext.getResponse().getCreatedDocuments()).hasSize(1);
    assertThat(responseDocumentContext.getResponse().getCreatedDocuments().get(0))
        .isEqualTo(documentReference);
  }

  static record ResultContainingDocument(DocumentContext context) {}
}
