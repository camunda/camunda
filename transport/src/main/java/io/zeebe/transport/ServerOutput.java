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
package io.zeebe.transport;

import io.zeebe.util.buffer.BufferWriter;

public interface ServerOutput {
  /**
   * Sends a message according to the single message protocol.
   *
   * <p>Returns false if the message cannot be currently written due to exhausted capacity. Throws
   * an exception if the request is not sendable at all (e.g. buffer writer throws exception).
   */
  boolean sendMessage(int streamId, BufferWriter writer);

  /**
   * Sends a response according to the request response protocol.
   *
   * <p>Returns null if the response cannot be currently written due to exhausted capacity. Throws
   * an exception if the response is not sendable at all (e.g. buffer writer throws exception).
   */
  boolean sendResponse(ServerResponse response);
}
