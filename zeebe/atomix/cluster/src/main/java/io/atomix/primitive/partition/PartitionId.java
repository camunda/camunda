/*
 * Copyright 2016-present Open Networking Foundation
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

import java.util.Comparator;
import org.jspecify.annotations.NonNull;

/** {@link PartitionMetadata} identifier. */
public record PartitionId(String group, int number) implements Comparable<PartitionId> {

  private static final Comparator<PartitionId> COMPARATOR =
      Comparator.comparing(PartitionId::group).thenComparingInt(PartitionId::number);

  public PartitionId {
    if (group == null) {
      throw new IllegalArgumentException("group cannot be null");
    }
    if (number < 0) {
      throw new IllegalArgumentException("partition number must be non-negative");
    }
  }

  @Override
  public int compareTo(@NonNull final PartitionId o) {
    return COMPARATOR.compare(this, o);
  }
}
