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
package io.zeebe.protocol.impl.data.cluster;

import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.ArrayProperty;
import io.zeebe.msgpack.property.IntegerProperty;
import io.zeebe.msgpack.value.ValueArray;

public class PartitionsResponseDto extends UnpackedObject {
  protected ArrayProperty<PartitionDto> partitions =
      new ArrayProperty<>("partitions", new PartitionDto());

  public PartitionsResponseDto() {
    declareProperty(partitions);
  }

  public void addPartition(final int id) {
    final PartitionDto partition = partitions.add();
    partition.setId(id);
  }

  public ValueArray<PartitionDto> getPartitions() {
    return partitions;
  }

  public static class PartitionDto extends UnpackedObject {
    protected IntegerProperty idProperty = new IntegerProperty("id");

    public PartitionDto() {
      declareProperty(idProperty);
    }

    public void setId(final int id) {
      this.idProperty.setValue(id);
    }

    public int getId() {
      return this.idProperty.getValue();
    }
  }
}
