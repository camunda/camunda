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
 * A Camunda document reference paired with its downloaded binary content, passed to {@link
 * MultimodalChatModelAdapter#generate(String, java.util.List)} so a custom {@link ChatModelAdapter}
 * preset can attach the content as structured blocks for its target LLM API.
 *
 * <p>{@link #getReference()} is the first-class Camunda primitive — read document id, store id,
 * content hash, and metadata (file name, mime type, …) from it directly.
 *
 * <p>If a document cannot be downloaded the judge evaluation fails fast; a {@code ResolvedDocument}
 * therefore always carries both a reference and its content.
 */
public interface ResolvedDocument {

  /**
   * @return the original Camunda document reference
   */
  DocumentReferenceResponse getReference();

  /**
   * @return the downloaded binary content
   */
  byte[] getContent();
}
