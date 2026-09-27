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
package io.atomix.raft.protocol;

import static com.google.common.base.MoreObjects.toStringHelper;

import java.util.Arrays;
import java.util.Objects;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The position an exporter on the leader has acknowledged, with the metadata it acknowledged it
 * with, handed over to the desired leader on {@link TimeoutNowRequest}.
 *
 * <p>A class rather than a record, so the Raft protocol's compatible field serialization can evolve
 * it like the requests that carry it.
 */
@NullMarked
public final class ExporterPosition {

  private final String exporterId;
  private final long position;
  private final byte[] metadata;

  public ExporterPosition(final String exporterId, final long position, final byte[] metadata) {
    this.exporterId = exporterId;
    this.position = position;
    this.metadata = metadata;
  }

  public String exporterId() {
    return exporterId;
  }

  public long position() {
    return position;
  }

  /** The exporter's metadata; empty if it has none. */
  public byte[] metadata() {
    return metadata;
  }

  @Override
  public int hashCode() {
    return Objects.hash(exporterId, position, Arrays.hashCode(metadata));
  }

  @Override
  public boolean equals(final @Nullable Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof final ExporterPosition other)) {
      return false;
    }
    return position == other.position
        && exporterId.equals(other.exporterId)
        && Arrays.equals(metadata, other.metadata);
  }

  @Override
  public String toString() {
    return toStringHelper(this)
        .add("exporterId", exporterId)
        .add("position", position)
        .add("metadataLength", metadata.length)
        .toString();
  }
}
