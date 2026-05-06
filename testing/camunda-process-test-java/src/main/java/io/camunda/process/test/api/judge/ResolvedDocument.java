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
 * A Camunda document reference resolved into its binary content, used to enrich the judge LLM
 * context.
 *
 * <p>Implementations are passed to {@link MultimodalChatModelAdapter#generate(String,
 * java.util.List)} so a custom {@link ChatModelAdapter} preset can attach the binary content as
 * structured content blocks for its target LLM API.
 *
 * <p>If {@link #getData()} is {@code null} and {@link #getErrorMessage()} is set, the reference
 * could not be resolved. Consumers should still surface the metadata so the judge can reason about
 * the gap.
 */
public interface ResolvedDocument {

  /**
   * @return the Camunda document id, or {@code null} if the reference itself was missing the id
   */
  String getDocumentId();

  /**
   * @return the original file name from the document metadata, or {@code null} if not present
   */
  String getFileName();

  /**
   * @return the MIME type from the document metadata, or {@code null} if not present
   */
  String getContentType();

  /**
   * @return the raw binary content of the document, or {@code null} if resolution failed
   */
  byte[] getData();

  /**
   * @return the error message describing why resolution failed, or {@code null} if the document was
   *     resolved successfully
   */
  String getErrorMessage();

  /**
   * @return {@code true} if the document was resolved successfully and {@link #getData()} is
   *     available
   */
  boolean isResolved();
}
