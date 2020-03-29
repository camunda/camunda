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
package io.atomix.core;

import io.atomix.core.registry.ClasspathScanningRegistry;
import io.atomix.utils.NamedType;
import java.util.Collection;

/** Atomix registry. */
public interface AtomixRegistry {

  /**
   * Creates a new registry.
   *
   * @return the registry instance
   */
  static AtomixRegistry registry() {
    return registry(Thread.currentThread().getContextClassLoader());
  }

  /**
   * Creates a new registry instance using the given class loader.
   *
   * @param classLoader the registry class loader
   * @return the registry instance
   */
  static AtomixRegistry registry(final ClassLoader classLoader) {
    return ClasspathScanningRegistry.builder().withClassLoader(classLoader).build();
  }

  /**
   * Returns the collection of registrations for the given type.
   *
   * @param type the type for which to return registrations
   * @param <T> the type for which to return registrations
   * @return a collection of registrations for the given type
   */
  <T extends NamedType> Collection<T> getTypes(Class<T> type);

  /**
   * Returns a named registration by type.
   *
   * @param type the registration type
   * @param name the registration name
   * @param <T> the registration type
   * @return the registration instance
   */
  <T extends NamedType> T getType(Class<T> type, String name);
}
