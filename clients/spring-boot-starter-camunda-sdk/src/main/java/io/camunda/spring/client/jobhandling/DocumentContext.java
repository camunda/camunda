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
package io.camunda.spring.client.jobhandling;

import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.client.api.worker.JobClient;
import io.camunda.spring.client.jobhandling.result.ResultDocumentContext;
import io.camunda.spring.client.jobhandling.result.ResultDocumentContextBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public interface DocumentContext {

  List<DocumentEntry> getDocuments();

  static ResultDocumentContextBuilder result() {
    return new ResultDocumentContext();
  }

  interface DocumentEntry {

    DocumentReferenceResponse getDocumentReference();

    InputStream getDocumentInputStream();

    default byte[] getDocumentBytes() {
      try (final InputStream in = getDocumentInputStream()) {
        return in.readAllBytes();
      } catch (final IOException e) {
        throw new RuntimeException("Error while handling document input stream", e);
      }
    }

    abstract class AbstractDocumentEntry implements DocumentEntry {
      protected final DocumentReferenceResponse documentReference;
      protected final JobClient jobClient;

      public AbstractDocumentEntry(
          final DocumentReferenceResponse documentReference, final JobClient jobClient) {
        this.documentReference = documentReference;
        this.jobClient = jobClient;
      }

      @Override
      public DocumentReferenceResponse getDocumentReference() {
        return documentReference;
      }

      @Override
      public InputStream getDocumentInputStream() {
        return jobClient.newDocumentContentGetRequest(documentReference).execute();
      }
    }
  }
}
