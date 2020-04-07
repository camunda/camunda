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
package io.atomix.primitive;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

/** Primitive registry. */
public interface PrimitiveRegistry {

  /**
   * Creates a new distributed primitive.
   *
   * @param name the primitive name
   * @param type the primitive type
   * @return a future to be completed with the primitive info
   */
  CompletableFuture<PrimitiveInfo> createPrimitive(String name, PrimitiveType type);

  /**
   * Deletes the given distributed primitive.
   *
   * @param name the primitive name
   * @return a future to be completed once the primitive info has been deleted
   */
  CompletableFuture<Void> deletePrimitive(String name);

  /**
   * Returns a collection of open primitives.
   *
   * @return a collection of open primitives
   */
  Collection<PrimitiveInfo> getPrimitives();

  /**
   * Returns a collection of open primitives of the given type.
   *
   * @param primitiveType the primitive type
   * @return a collection of open primitives of the given type
   */
  Collection<PrimitiveInfo> getPrimitives(PrimitiveType primitiveType);

  /**
   * Returns the info for a single primitive.
   *
   * @param name the primitive name
   * @return the primitive info
   */
  PrimitiveInfo getPrimitive(String name);
}
