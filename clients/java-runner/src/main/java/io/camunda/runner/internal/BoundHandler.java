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
package io.camunda.runner.internal;

import io.camunda.runner.Job;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Discriminated union recording how a user supplied a handler for a given element. Phase 2's runner
 * pipeline reads this back to wire the handler into a {@link JobHandlerAdapter}.
 */
public sealed interface BoundHandler permits BoundHandler.OfFunction, BoundHandler.OfConsumer {

  record OfFunction(Function<Job, Map<String, Object>> fn) implements BoundHandler {}

  record OfConsumer(Consumer<Job> consumer) implements BoundHandler {}
}
