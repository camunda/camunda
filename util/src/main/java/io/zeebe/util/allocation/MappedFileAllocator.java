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
package io.zeebe.util.allocation;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel.MapMode;

/** Allocates a buffer in a mapped file. */
public class MappedFileAllocator implements BufferAllocator {

  private final File mappedFile;

  public MappedFileAllocator(File mappedFile) {
    super();
    this.mappedFile = mappedFile;
  }

  @Override
  public AllocatedBuffer allocate(int capacity) {
    RandomAccessFile raf = null;

    try {
      raf = new RandomAccessFile(mappedFile, "rw");

      final MappedByteBuffer mappedBuffer = raf.getChannel().map(MapMode.READ_WRITE, 0, capacity);

      return new AllocatedMappedFile(mappedBuffer, raf);
    } catch (Exception e) {
      if (raf != null) {
        try {
          raf.close();
        } catch (IOException e1) {
          // ignore silently
        }
      }

      throw new RuntimeException(
          "Could not map file " + mappedFile + " into memory: " + e.getMessage(), e);
    }
  }
}
