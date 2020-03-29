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
package io.atomix.primitive.partition;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.utils.event.AbstractEvent;

/** Primary election event. */
public class PrimaryElectionEvent extends AbstractEvent<PrimaryElectionEvent.Type, PrimaryTerm> {

  private final PartitionId partitionId;

  public PrimaryElectionEvent(
      final Type type, final PartitionId partitionId, final PrimaryTerm subject) {
    super(type, subject);
    this.partitionId = partitionId;
  }

  /**
   * Returns the partition ID.
   *
   * @return the election partition
   */
  public PartitionId partitionId() {
    return partitionId;
  }

  /**
   * Returns the election term.
   *
   * @return the election term
   */
  public PrimaryTerm term() {
    return subject();
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("partition", partitionId).add("term", term()).toString();
  }

  /** Returns the election event type. */
  public enum Type {
    CHANGED
  }
}
