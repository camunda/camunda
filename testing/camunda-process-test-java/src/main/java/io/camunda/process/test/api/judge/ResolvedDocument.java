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
package io.camunda.process.test.api.judge;

/**
 * A Camunda document reference paired with its downloaded binary content, handed to {@link
 * MultimodalChatModelAdapter#generate(String, java.util.List)}. Document resolution is fail-fast,
 * so {@link #getContent()} is always populated.
 */
public interface ResolvedDocument {

  /** Returns the Camunda document id. */
  String getDocumentId();

  /** Returns the document store id, or {@code null} if the reference does not specify one. */
  String getStoreId();

  /**
   * Returns the document content hash as reported by the Camunda document store, or {@code null}.
   */
  String getContentHash();

  /** Returns the file name from the document metadata, or {@code null} if unset. */
  String getFileName();

  /** Returns the MIME content type from the document metadata, or {@code null} if unset. */
  String getContentType();

  /** Returns the downloaded document content. Never {@code null} (resolution is fail-fast). */
  byte[] getContent();
}
