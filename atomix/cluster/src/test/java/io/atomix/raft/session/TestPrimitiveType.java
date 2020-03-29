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
package io.atomix.raft.session;

import io.atomix.primitive.PrimitiveBuilder;
import io.atomix.primitive.PrimitiveManagementService;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.config.PrimitiveConfig;
import io.atomix.primitive.service.PrimitiveService;
import io.atomix.primitive.service.ServiceConfig;

/** Test primitive type. */
public class TestPrimitiveType implements PrimitiveType {

  static final TestPrimitiveType INSTANCE = new TestPrimitiveType();

  /**
   * Returns a singleton instance.
   *
   * @return a singleton primitive type instance
   */
  public static TestPrimitiveType instance() {
    return INSTANCE;
  }

  @Override
  public String name() {
    return "test";
  }

  @Override
  public PrimitiveConfig newConfig() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrimitiveBuilder newBuilder(
      final String primitiveName,
      final PrimitiveConfig config,
      final PrimitiveManagementService managementService) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrimitiveService newService(final ServiceConfig config) {
    throw new UnsupportedOperationException();
  }
}
