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

import static io.camunda.spring.client.TestUtil.getBytes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.CreateDocumentBatchCommandStep1;
import io.camunda.client.api.command.CreateDocumentBatchCommandStep1.CreateDocumentBatchCommandStep2;
import io.camunda.client.api.response.DocumentReferenceBatchResponse;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.response.DocumentReferenceResponseImpl;
import io.camunda.spring.client.jobhandling.DocumentContext;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ResultDocumentContextTest {

  @Test
  void shouldSerialize() {
    final CamundaClient camundaClient = mock(CamundaClient.class);
    final CreateDocumentBatchCommandStep1 step1 = mock(CreateDocumentBatchCommandStep1.class);
    when(camundaClient.newCreateDocumentBatchCommand()).thenReturn(step1);
    final CreateDocumentBatchCommandStep2 step2 = mock(CreateDocumentBatchCommandStep2.class);
    when(step1.addDocument()).thenReturn(step2);
    when(step2.content(anyString())).thenReturn(step2);
    when(step2.done()).thenReturn(step1);
    final DocumentReferenceBatchResponse batchResponse = mock(DocumentReferenceBatchResponse.class);
    when(step1.execute()).thenReturn(batchResponse);
    when(batchResponse.isSuccessful()).thenReturn(true);
    final JsonMapper jsonMapper = new CamundaObjectMapper();
    final String mockDocRef = new String(getBytes("/document/documentReference.json"));
    final DocumentReferenceResponse documentReference =
        jsonMapper.fromJson(mockDocRef, DocumentReferenceResponseImpl.class);
    when(batchResponse.getCreatedDocuments()).thenReturn(List.of(documentReference));
    final ResultDocumentContext resultDocumentContext =
        (ResultDocumentContext)
            DocumentContext.result()
                .addDocument("test.txt", b -> b.content("Hello World!"))
                .build();
    resultDocumentContext.processDocumentBuilders(camundaClient);
    final String json = jsonMapper.toJson(resultDocumentContext);
    assertThat(json)
        .isEqualTo(
            "["
                + mockDocRef
                    .replaceAll("\n", "")
                    .replaceAll("\r", "")
                    .replaceAll("\t", "")
                    .replaceAll(" ", "")
                + "]");
  }
}
