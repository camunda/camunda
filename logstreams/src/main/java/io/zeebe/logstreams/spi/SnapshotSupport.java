/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.logstreams.spi;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Interface to be implemented by resources which are used by stream processors and support
 * snapshots.
 */
public interface SnapshotSupport {
  /**
   * write a snapshot to the provided output stream
   *
   * @param outputStream the stream to write to
   * @return the size of the snapshot in bytes
   */
  long writeSnapshot(OutputStream outputStream) throws Exception;

  /**
   * read a snapshot from the provided input stream.
   *
   * @param inputStream the stream to read from
   */
  void recoverFromSnapshot(InputStream inputStream) throws Exception;

  /** Set the snapshot in the initial state. */
  void reset();
}
