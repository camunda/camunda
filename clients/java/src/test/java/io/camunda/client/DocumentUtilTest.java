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
package io.camunda.client;

import static io.camunda.client.TestUtil.getBytes;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.client.impl.CamundaObjectMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

public class DocumentUtilTest {
  @Test
  void shouldReturnDocumentReferences() {
    final JsonMapper jsonMapper = new CamundaObjectMapper();
    final JobClient jobClient = CamundaClient.newClient();
    final Object documentReference =
        Arrays.asList(
            jsonMapper.fromJsonAsMap(
                new String(getBytes("/document/test-document-reference.json"))));
    final List<DocumentReferenceResponse> documentContext =
        DocumentUtil.createDocumentContext(jobClient, documentReference);
    assertThat(documentContext).hasSize(1);
    assertThat(documentContext.get(0)).isNotNull();
    assertThat(documentContext.get(0).getDocumentType()).isEqualTo("camunda");
    assertThat(documentContext.get(0).getDocumentId()).isEqualTo("document-id");
    assertThat(documentContext.get(0).getContentHash()).isEqualTo("content-hash");
    assertThat(documentContext.get(0).getMetadata()).isNotNull();
    assertThat(documentContext.get(0).getMetadata().getContentType()).isEqualTo("content-type");
    assertThat(documentContext.get(0).getMetadata().getFileName()).isEqualTo("file-name");
    assertThat(documentContext.get(0).getMetadata().getExpiresAt())
        .isEqualTo("2025-06-28T07:32:28.93912+02:00");
  }
}
