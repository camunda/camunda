/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.primitive;

import io.atomix.primitive.AsyncPrimitive;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Test primitive. */
public interface TestPrimitive extends AsyncPrimitive {

  CompletableFuture<Long> write(String value);

  CompletableFuture<Long> read();

  CompletableFuture<Long> sendEvent(boolean sender);

  CompletableFuture<Void> onEvent(Consumer<Long> callback);

  CompletableFuture<Void> onExpire(Consumer<String> callback);

  CompletableFuture<Void> onClose(Consumer<String> callback);
}
