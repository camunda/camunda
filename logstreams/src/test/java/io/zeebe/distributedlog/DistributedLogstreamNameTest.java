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
package io.zeebe.distributedlog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.atomix.primitive.partition.PartitionId;
import io.zeebe.distributedlog.impl.DistributedLogstreamName;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;

public class DistributedLogstreamNameTest {

  private static final int PARTITION_COUNT = 8;
  private static final List<PartitionId> PARTITION_IDS =
      IntStream.range(0, PARTITION_COUNT)
          .boxed()
          .map(i -> PartitionId.from("test", i))
          .collect(Collectors.toList());

  private static final DistributedLogstreamName PARTITIONER =
      DistributedLogstreamName.getInstance();

  @Test
  public void shouldFindPartition() {
    final String partitionKey = DistributedLogstreamName.getPartitionKey(5);
    final PartitionId id = PARTITIONER.partition(partitionKey, PARTITION_IDS);
    assertThat(id.id()).isEqualTo(5);
  }

  @Test
  public void shouldThrowExceptionForNonExistingPartition() {
    final String partitionKey = DistributedLogstreamName.getPartitionKey(10);
    assertThatThrownBy(() -> PARTITIONER.partition(partitionKey, PARTITION_IDS))
        .isInstanceOf(NoSuchElementException.class);
  }

  @Test
  public void shouldThrowExceptionForWrongKeyFormat() {
    final String wrongKey = "SomeKey";
    assertThatThrownBy(() -> PARTITIONER.partition(wrongKey, PARTITION_IDS)).isNotNull();
  }
}
