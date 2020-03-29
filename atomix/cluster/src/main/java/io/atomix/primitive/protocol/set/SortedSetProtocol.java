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
package io.atomix.primitive.protocol.set;

import com.google.common.annotations.Beta;
import io.atomix.primitive.PrimitiveManagementService;
import io.atomix.utils.serializer.Serializer;

/** Sorted set protocol. */
@Beta
public interface SortedSetProtocol extends SetProtocol {

  /**
   * Returns a new set delegate.
   *
   * @param name the set name
   * @param serializer the set element serializer
   * @param managementService the primitive management service
   * @param <E> the set element type
   * @return a new set delegate
   */
  <E> SortedSetDelegate<E> newSortedSetDelegate(
      String name, Serializer serializer, PrimitiveManagementService managementService);
}
