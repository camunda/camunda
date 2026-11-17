/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.atomix.raft;

/**
 * Notified when the local member finds that it is fenced: its id and incarnation are on the fence
 * list of a committed configuration, or a member rejected one of its requests because of a
 * committed fence entry. The server then stays {@link RaftServer.Role#INACTIVE}. The owner of the
 * server is expected to delete the local data and join again with a new incarnation.
 */
@FunctionalInterface
public interface FencedListener {

  /**
   * @param incarnation the incarnation of the local member that is fenced
   */
  void onFenced(long incarnation);
}
