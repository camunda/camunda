/*
 * Copyright 2014-present Open Networking Foundation
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
package io.atomix.cluster;

import java.util.UUID;

/** Controller cluster identity. */
public class MemberId extends NodeId {

  public MemberId(final String id) {
    super(id);
  }

  /**
   * Creates a new cluster node identifier from the specified string.
   *
   * @return node id
   */
  public static MemberId anonymous() {
    return new MemberId(UUID.randomUUID().toString());
  }

  /**
   * Creates a new cluster node identifier from the specified string.
   *
   * @param id string identifier
   * @return node id
   */
  public static MemberId from(final String id) {
    return new MemberId(id);
  }
}
