/*
 * Copyright 2018-present Open Networking Foundation
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
package io.atomix.core.test.protocol;

import io.atomix.primitive.partition.Partitioner;
import io.atomix.primitive.protocol.PrimitiveProtocol;
import io.atomix.primitive.protocol.PrimitiveProtocolConfig;

/** Test protocol config. */
public class TestProtocolConfig extends PrimitiveProtocolConfig<TestProtocolConfig> {
  private String group = "test";
  private int partitions = 3;
  private Partitioner<String> partitioner = Partitioner.MURMUR3;

  @Override
  public PrimitiveProtocol.Type getType() {
    return TestProtocol.TYPE;
  }

  /**
   * Returns the partition group.
   *
   * @return the partition group
   */
  public String getGroup() {
    return group;
  }

  /**
   * Sets the partition group.
   *
   * @param group the partition group
   * @return the test protocol configuration
   */
  public TestProtocolConfig setGroup(final String group) {
    this.group = group;
    return this;
  }

  /**
   * Returns the number of partitions to use in tests.
   *
   * @return the number of partitions to use in tests
   */
  public int getPartitions() {
    return partitions;
  }

  /**
   * Sets the number of partitions to use in tests.
   *
   * @param partitions the number of partitions to use in tests
   * @return the test protocol configuration
   */
  public TestProtocolConfig setPartitions(final int partitions) {
    this.partitions = partitions;
    return this;
  }

  /**
   * Returns the protocol partitioner.
   *
   * @return the protocol partitioner
   */
  public Partitioner<String> getPartitioner() {
    return partitioner;
  }

  /**
   * Sets the protocol partitioner.
   *
   * @param partitioner the protocol partitioner
   * @return the protocol configuration
   */
  public TestProtocolConfig setPartitioner(final Partitioner<String> partitioner) {
    this.partitioner = partitioner;
    return this;
  }
}
