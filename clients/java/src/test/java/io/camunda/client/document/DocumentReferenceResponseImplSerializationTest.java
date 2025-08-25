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
package io.camunda.client.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.impl.CamundaObjectMapper;
import io.camunda.client.impl.command.StreamUtil;
import io.camunda.client.impl.response.DocumentReferenceResponseImpl;
import io.camunda.client.protocol.rest.DocumentMetadata;
import io.camunda.client.protocol.rest.DocumentReference;
import io.camunda.client.protocol.rest.DocumentReference.CamundaDocumentTypeEnum;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

public class DocumentReferenceResponseImplSerializationTest {
  private final JsonMapper jsonMapper = new CamundaObjectMapper();
  private final String documentReferenceJson =
      loadResourceFromClasspath("document/test-document-reference.json");

  private static String loadResourceFromClasspath(final String resourceName) {
    try (final InputStream in =
        DocumentReferenceResponseImplSerializationTest.class
            .getClassLoader()
            .getResourceAsStream(resourceName)) {
      return new String(StreamUtil.readInputStream(in));
    } catch (final IOException e) {
      throw new RuntimeException(
          "Error while reading resource " + resourceName + " from classpath", e);
    }
  }

  @Test
  void shouldSerializeDocumentReferenceResponse() {
    final DocumentReference documentReference =
        new DocumentReference()
            .documentId("document-id")
            .contentHash("content-hash")
            .camundaDocumentType(CamundaDocumentTypeEnum.CAMUNDA)
            .metadata(
                new DocumentMetadata()
                    .contentType("content-type")
                    .expiresAt("2025-06-28T07:32:28.93912+02:00")
                    .fileName("file-name"));
    final DocumentReferenceResponse response = new DocumentReferenceResponseImpl(documentReference);
    final String json = jsonMapper.toJson(response);
    assertThat(jsonMapper.fromJsonAsMap(json))
        .isEqualTo(jsonMapper.fromJsonAsMap(documentReferenceJson));
  }

  @Test
  void shouldDeserializeDocumentReferenceResponse() {
    final DocumentReferenceResponse response =
        jsonMapper.fromJson(documentReferenceJson, DocumentReferenceResponseImpl.class);
    assertThat(response).isNotNull();
    assertThat(response.getDocumentType()).isEqualTo("camunda");
    assertThat(response.getDocumentId()).isEqualTo("document-id");
    assertThat(response.getContentHash()).isEqualTo("content-hash");
    assertThat(response.getMetadata()).isNotNull();
    assertThat(response.getMetadata().getContentType()).isEqualTo("content-type");
    assertThat(response.getMetadata().getFileName()).isEqualTo("file-name");
    assertThat(response.getMetadata().getExpiresAt()).isEqualTo("2025-06-28T07:32:28.93912+02:00");
  }
}
