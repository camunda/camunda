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
 * Extends {@link ChatModelAdapter} so an implementation can additionally receive resolved Camunda
 * documents as structured content blocks. Adapters that only implement {@link ChatModelAdapter}
 * receive a text-only prompt and no documents are resolved.
 *
 * <p>The {@code prompt} passed to {@link #generate(String, List)} is already wrapped by the
 * framework so that data inside it is signalled to the judge as untrusted. Implementations should
 * follow common prompt-injection mitigation practices when turning the documents into content
 * blocks for their target provider:
 *
 * <ul>
 *   <li>prefer attaching binaries as native provider content blocks (image, PDF, …) rather than
 *       decoding them into free text;
 *   <li>if a body must be inlined as text, wrap it in a clearly delimited block so the judge cannot
 *       confuse it with instructions;
 *   <li>escape document metadata (file name, content type) before embedding it in any delimiter or
 *       attribute;
 *   <li>keep document content in the user-message scope; do not lift it into a system message.
 * </ul>
 *
 * Refer to the Camunda Process Test documentation for the full guidance and a worked example.
 */
public interface MultimodalChatModelAdapter extends ChatModelAdapter {

  /** Generates a response for the given prompt with the resolved documents attached. */
  String generate(String prompt, List<ResolvedDocument> documents);
}
