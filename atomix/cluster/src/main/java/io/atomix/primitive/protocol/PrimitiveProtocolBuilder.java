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
package io.atomix.primitive.protocol;

import static com.google.common.base.Preconditions.checkNotNull;

import io.atomix.utils.Builder;

/** Primitive protocol builder. */
public abstract class PrimitiveProtocolBuilder<
        B extends PrimitiveProtocolBuilder<B, C, P>,
        C extends PrimitiveProtocolConfig<C>,
        P extends PrimitiveProtocol>
    implements Builder<P> {
  protected final C config;

  protected PrimitiveProtocolBuilder(final C config) {
    this.config = checkNotNull(config);
  }
}
