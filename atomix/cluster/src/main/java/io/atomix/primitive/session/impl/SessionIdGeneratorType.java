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
package io.atomix.primitive.session.impl;

import static com.google.common.base.MoreObjects.toStringHelper;

import io.atomix.primitive.PrimitiveBuilder;
import io.atomix.primitive.PrimitiveManagementService;
import io.atomix.primitive.PrimitiveType;
import io.atomix.primitive.config.PrimitiveConfig;
import io.atomix.primitive.service.PrimitiveService;
import io.atomix.primitive.service.ServiceConfig;

/** Session ID generator primitive type. */
public class SessionIdGeneratorType implements PrimitiveType {
  private static final String NAME = "SESSION_ID_GENERATOR";
  private static final SessionIdGeneratorType TYPE = new SessionIdGeneratorType();

  /**
   * Returns a new session ID generator type.
   *
   * @return a new session ID generator type
   */
  public static SessionIdGeneratorType instance() {
    return TYPE;
  }

  @Override
  public String name() {
    return NAME;
  }

  @Override
  public PrimitiveConfig newConfig() {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrimitiveBuilder newBuilder(
      final String name,
      final PrimitiveConfig config,
      final PrimitiveManagementService managementService) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PrimitiveService newService(final ServiceConfig config) {
    return new SessionIdGeneratorService();
  }

  @Override
  public String toString() {
    return toStringHelper(this).add("name", name()).toString();
  }
}
