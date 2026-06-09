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
 */
public interface MultimodalChatModelAdapter extends ChatModelAdapter {

  /** Generates a response for the given prompt with the resolved documents attached. */
  String generate(String prompt, List<ResolvedDocument> documents);
}
