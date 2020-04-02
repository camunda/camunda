/*
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.storage.snapshot.impl;

import io.atomix.raft.storage.snapshot.SnapshotChunk;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class DefaultSnapshotChunk implements SnapshotChunk {
  private final ByteBuffer id;
  private final ByteBuffer data;

  public DefaultSnapshotChunk(final int offset, final ByteBuffer data) {
    this.id =
        ByteBuffer.allocateDirect(Integer.BYTES).putInt(0, offset).order(ByteOrder.BIG_ENDIAN);
    this.data = data;
  }

  @Override
  public ByteBuffer id() {
    return id;
  }

  @Override
  public ByteBuffer data() {
    return data;
  }
}
