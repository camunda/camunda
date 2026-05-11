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

import io.camunda.client.api.response.DocumentReferenceResponse;

/**
 * A Camunda document reference paired with the outcome of resolving its binary content, passed to
 * {@link MultimodalChatModelAdapter#generate(String, java.util.List)} so a custom {@link
 * ChatModelAdapter} preset can attach the content as structured blocks for its target LLM API.
 *
 * <p>{@link #getReference()} is the first-class Camunda primitive — read document id, store id,
 * content hash, and metadata (file name, mime type, …) from it directly.
 *
 * <p>If {@link #getContent()} is {@code null} and {@link #getErrorMessage()} is set, the reference
 * could not be resolved. Consumers should still surface the metadata so the judge can reason about
 * the gap.
 */
public interface ResolvedDocument {

  /**
   * @return the original Camunda document reference; never {@code null} except when the reference
   *     node itself could not be parsed
   */
  DocumentReferenceResponse getReference();

  /**
   * @return the downloaded binary content, or {@code null} if resolution failed
   */
  byte[] getContent();

  /**
   * @return the failure reason, or {@code null} when resolution succeeded
   */
  String getErrorMessage();

  /**
   * @return {@code true} if the document was resolved successfully and {@link #getContent()} is
   *     available
   */
  boolean isResolved();
}
