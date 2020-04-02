/*
 * Copyright 2017-present Open Networking Foundation
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
package io.atomix.primitive.session;

import static com.google.common.base.MoreObjects.toStringHelper;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Objects;

/** Primitive session metadata. */
public final class SessionMetadata {
  private final long id;
  private final String name;
  private final String type;

  public SessionMetadata(final long id, final String name, final String type) {
    this.id = id;
    this.name = checkNotNull(name, "name cannot be null");
    this.type = checkNotNull(type, "type cannot be null");
  }

  /**
   * Returns the globally unique session identifier.
   *
   * @return The globally unique session identifier.
   */
  public SessionId sessionId() {
    return SessionId.from(id);
  }

  /**
   * Returns the session name.
   *
   * @return The session name.
   */
  public String primitiveName() {
    return name;
  }

  /**
   * Returns the session type.
   *
   * @return The session type.
   */
  public String primitiveType() {
    return type;
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, type, name);
  }

  @Override
  public boolean equals(final Object object) {
    if (object instanceof SessionMetadata) {
      final SessionMetadata metadata = (SessionMetadata) object;
      return metadata.id == id
          && Objects.equals(metadata.name, name)
          && Objects.equals(metadata.type, type);
    }
    return false;
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("id", id).add("name", name).add("type", type).toString();
  }
}
