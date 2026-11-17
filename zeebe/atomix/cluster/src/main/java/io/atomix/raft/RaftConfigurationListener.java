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

import io.atomix.raft.storage.system.Configuration;

/**
 * Notified on the Raft thread when the local member applies a configuration, and again when that
 * configuration is committed. Configurations take effect when they are appended, so an applied
 * configuration may still be replaced by an older one if its entry is truncated; a listener is then
 * called with that older configuration.
 */
@FunctionalInterface
public interface RaftConfigurationListener {

  /**
   * @param configuration the configuration: typed members with incarnations, fence list, voter
   *     policy, index, and old members while a joint change is in progress
   * @param committed true if the configuration is known to be committed
   */
  void onConfigurationChanged(Configuration configuration, boolean committed);
}
