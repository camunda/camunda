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

import io.zeebe.util.Loggers;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import org.slf4j.Logger;

public class AllocatedMappedFile extends AllocatedBuffer {
  private static final Logger LOG = Loggers.IO_LOGGER;

  protected final RandomAccessFile raf;

  public AllocatedMappedFile(ByteBuffer buffer, RandomAccessFile raf) {
    super(buffer);
    this.raf = raf;
  }

  @Override
  public void doClose() {
    try {
      raf.close();
    } catch (IOException e) {
      LOG.warn("Failed to close mapped file.", e);
    }
  }

  public RandomAccessFile getFile() {
    return raf;
  }
}
