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
package io.atomix.storage.journal;

import java.io.Closeable;

/**
 * Journal.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public interface Journal<E> extends Closeable {

  /**
   * Returns the journal writer.
   *
   * @return The journal writer.
   */
  JournalWriter<E> writer();

  /**
   * Opens a new journal reader.
   *
   * @param index The index at which to start the reader.
   * @return A new journal reader.
   */
  JournalReader<E> openReader(long index);

  /**
   * Opens a new journal reader.
   *
   * @param index The index at which to start the reader.
   * @param mode the reader mode
   * @return A new journal reader.
   */
  JournalReader<E> openReader(long index, JournalReader.Mode mode);

  /**
   * Returns a boolean indicating whether the journal is open.
   *
   * @return Indicates whether the journal is open.
   */
  boolean isOpen();

  @Override
  void close();
}
