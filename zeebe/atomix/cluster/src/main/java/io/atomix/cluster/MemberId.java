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

  /**
   * Creates a MemberId for the given node ID and version.
   *
   * <p>This factory method automatically selects the appropriate type based on the version:
   *
   * <ul>
   *   <li>If version is 0, a simple {@link MemberId} is created (for static/FIXED node ID config)
   *   <li>If version is greater than 0, a {@link VersionedMemberId} is created (for dynamic/S3 node
   *       ID config)
   * </ul>
   *
   * @param nodeId the node ID
   * @param version the version of the node ID (0 means no versioning)
   * @return a {@link MemberId} or {@link VersionedMemberId} depending on the version
   */
  public static MemberId from(final int nodeId, final long version) {
    if (version < 0) {
      throw new IllegalArgumentException("Expected version to be non-negative, but got " + version);
    }
    final String id = String.valueOf(nodeId);
    return version > 0 ? new VersionedMemberId(id, version) : new MemberId(id);
  }

  /**
   * Returns the version of this member ID.
   *
   * <p>For static node ID configuration, this returns 0. For dynamic node ID configuration (e.g.,
   * S3), this returns the version/lease generation of the node ID.
   *
   * @return the version number, or 0 if not versioned
   */
  public long getIdVersion() {
    return 0;
  }
}
