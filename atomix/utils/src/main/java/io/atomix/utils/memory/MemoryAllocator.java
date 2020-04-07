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
package io.atomix.utils.memory;

/**
 * Memory allocator.
 *
 * <p>Memory allocators handle allocation of memory for {@link Memory} objects.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public interface MemoryAllocator<T extends Memory> {

  /**
   * Allocates memory.
   *
   * @param size The count of the memory to allocate.
   * @return The allocated memory.
   */
  T allocate(int size);

  /**
   * Reallocates the given memory.
   *
   * <p>When the memory is reallocated, the memory address for the given {@link Memory} instance may
   * change. The returned {@link Memory} instance will contain the updated address and count.
   *
   * @param memory The memory to reallocate.
   * @param size The count to which to reallocate the given memory.
   * @return The reallocated memory.
   */
  T reallocate(T memory, int size);
}
