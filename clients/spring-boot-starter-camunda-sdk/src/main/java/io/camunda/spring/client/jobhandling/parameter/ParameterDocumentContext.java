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
package io.camunda.spring.client.jobhandling.parameter;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.spring.client.jobhandling.DocumentContext;
import io.camunda.spring.client.jobhandling.DocumentContext.DocumentEntry.AbstractDocumentEntry;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;

public class ParameterDocumentContext implements DocumentContext {
  private final List<DocumentReferenceResponse> documentReferences;
  private final CamundaClient camundaClient;
  private final boolean optional;

  public ParameterDocumentContext(
      final List<DocumentReferenceResponse> documentReferences,
      final CamundaClient camundaClient,
      final boolean optional) {
    this.documentReferences = documentReferences;
    this.camundaClient = camundaClient;
    this.optional = optional;
  }

  @Override
  public List<DocumentEntry> getDocuments() {
    return documentReferences.stream()
        .map(r -> new ParameterDocumentEntry(r, camundaClient, optional))
        .map(DocumentEntry.class::cast)
        .toList();
  }

  public static class ParameterDocumentEntry extends AbstractDocumentEntry {
    private final boolean optional;

    public ParameterDocumentEntry(
        final DocumentReferenceResponse documentReference,
        final CamundaClient camundaClient,
        final boolean optional) {
      super(documentReference, camundaClient);
      this.optional = optional;
    }

    @Override
    public InputStream getDocumentInputStream() {
      final InputStream in = super.getDocumentInputStream();
      if (!optional) {
        return Objects.requireNonNull(in, "Document content is null");
      } else {
        return in;
      }
    }
  }
}
