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

import io.atomix.primitive.protocol.PrimitiveProtocolBuilder;

/** Test protocol builder. */
public class TestProtocolBuilder
    extends PrimitiveProtocolBuilder<TestProtocolBuilder, TestProtocolConfig, TestProtocol> {
  TestProtocolBuilder(final TestProtocolConfig config) {
    super(config);
  }

  /**
   * Sets the number of test partitions.
   *
   * @param partitions the number of test partitions
   * @return the test protocol builder
   */
  public TestProtocolBuilder withNumPartitions(final int partitions) {
    config.setPartitions(partitions);
    return this;
  }

  @Override
  public TestProtocol build() {
    return new TestProtocol(config);
  }
}
