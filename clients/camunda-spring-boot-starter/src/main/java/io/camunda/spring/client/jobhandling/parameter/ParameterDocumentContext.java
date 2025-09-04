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
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.DocumentLinkResponse;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.spring.client.jobhandling.DocumentContext;
import java.io.InputStream;
import java.util.List;

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

  public static class ParameterDocumentEntry implements DocumentEntry {
    private final boolean optional;
    private final DocumentReferenceResponse documentReference;
    private final CamundaClient camundaClient;

    public ParameterDocumentEntry(
        final DocumentReferenceResponse documentReference,
        final CamundaClient camundaClient,
        final boolean optional) {
      this.optional = optional;
      this.documentReference = documentReference;
      this.camundaClient = camundaClient;
    }

    @Override
    public DocumentReferenceResponse getDocumentReference() {
      return documentReference;
    }

    @Override
    public InputStream getDocumentInputStream() {
      InputStream in = null;
      try {
        in = camundaClient.newDocumentContentGetRequest(documentReference).execute();
      } catch (final Exception e) {
        if (!optional) {
          throw new ClientException(
              "Could not get document with id " + documentReference.getDocumentId(), e);
        }
      }
      if (!optional && in == null) {
        throw new ClientException(
            "Document input stream is null for document with id "
                + documentReference.getDocumentId());
      } else {
        return in;
      }
    }

    @Override
    public DocumentLinkResponse getDocumentLink() {
      return camundaClient.newCreateDocumentLinkCommand(documentReference).execute();
    }
  }
}
