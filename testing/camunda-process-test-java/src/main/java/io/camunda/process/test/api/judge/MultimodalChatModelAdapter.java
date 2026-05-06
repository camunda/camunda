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

import java.util.List;

/**
 * A {@link ChatModelAdapter} that can additionally attach binary content (images, PDFs, etc.) as
 * structured content blocks alongside the prompt.
 *
 * <p>Adapters that implement this interface receive resolved Camunda document content when the
 * judge has document resolution enabled (see {@link JudgeConfig#withResolveDocuments(boolean)}).
 * Adapters that only implement {@link ChatModelAdapter} receive a text-only prompt and no documents
 * will be resolved.
 *
 * <p>Custom presets can implement this single interface to support both calling modes:
 *
 * <pre>
 *   public class MyAdapter implements MultimodalChatModelAdapter {
 *     public String generate(String prompt) { ... }                                  // text-only
 *     public String generate(String prompt, List&lt;ResolvedDocument&gt; documents) { ... } // multimodal
 *   }
 * </pre>
 */
public interface MultimodalChatModelAdapter extends ChatModelAdapter {

  /**
   * Generates a response for the given prompt with attached document content.
   *
   * @param prompt the textual prompt (already includes expectation, actual value, and rubric)
   * @param documents the resolved Camunda documents to attach as structured content blocks
   * @return the generated response
   */
  String generate(String prompt, List<ResolvedDocument> documents);
}
