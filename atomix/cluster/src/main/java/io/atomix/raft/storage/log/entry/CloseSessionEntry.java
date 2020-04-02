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
package io.atomix.raft.storage.log.entry;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.utils.misc.TimestampPrinter;

/** Close session entry. */
public class CloseSessionEntry extends SessionEntry {

  private final boolean expired;
  private final boolean delete;

  public CloseSessionEntry(
      final long term,
      final long timestamp,
      final long session,
      final boolean expired,
      final boolean delete) {
    super(term, timestamp, session);
    this.expired = expired;
    this.delete = delete;
  }

  /**
   * Returns whether the session is expired.
   *
   * @return Indicates whether the session is expired.
   */
  public boolean expired() {
    return expired;
  }

  /**
   * Returns whether to delete the service.
   *
   * @return whether to delete the service
   */
  public boolean delete() {
    return delete;
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("term", term)
        .add("timestamp", new TimestampPrinter(timestamp))
        .add("session", session)
        .add("expired", expired)
        .add("delete", delete)
        .toString();
  }
}
