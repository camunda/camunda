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
package io.atomix.utils.concurrent;

/**
 * Reference counting interface.
 *
 * <p>Types that implement {@code ReferenceCounted} can be counted for references and thus used to
 * clean up resources once a given instance of an object is no longer in use.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public interface ReferenceCounted<T> extends AutoCloseable {

  /**
   * Acquires a reference.
   *
   * @return The acquired reference.
   */
  T acquire();

  /**
   * Releases a reference.
   *
   * @return Indicates whether all references to the object have been released.
   */
  boolean release();

  /**
   * Returns the number of open references.
   *
   * @return The number of open references.
   */
  int references();

  /** Defines an exception free close implementation. */
  @Override
  void close();
}
