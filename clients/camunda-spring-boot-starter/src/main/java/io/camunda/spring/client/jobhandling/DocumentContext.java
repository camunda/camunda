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

import io.camunda.client.api.response.DocumentLinkResponse;
import io.camunda.client.api.response.DocumentReferenceResponse;
import io.camunda.spring.client.jobhandling.result.ResultDocumentContext;
import io.camunda.spring.client.jobhandling.result.ResultDocumentContextBuilder;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * A self-contained, fully functional context to handle a list of document references, can be
 * injected to a job worker function or be used as part of a result
 */
public interface DocumentContext {

  /**
   * @return a list of {@link DocumentEntry} objects
   */
  List<DocumentEntry> getDocuments();

  /**
   * @return a {@link ResultDocumentContextBuilder} to collect data for a document reference result
   */
  static ResultDocumentContextBuilder result() {
    return new ResultDocumentContext();
  }

  /** A self-contained, fully functional context of one entry in a {@link DocumentContext} */
  interface DocumentEntry {

    /**
     * @return the document reference of the entry
     */
    DocumentReferenceResponse getDocumentReference();

    /**
     * @return the input stream of the referenced document, needs to be closed after usage
     */
    InputStream getDocumentInputStream();

    /**
     * @return the bytes of the reference document
     */
    default byte[] getDocumentBytes() {
      try (final InputStream in = getDocumentInputStream()) {
        return in.readAllBytes();
      } catch (final IOException e) {
        throw new RuntimeException("Error while handling document input stream", e);
      }
    }

    /**
     * @return a link to the referenced document
     */
    DocumentLinkResponse getDocumentLink();
  }
}
