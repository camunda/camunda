/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.clustering.base.raft;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.zeebe.util.FileUtil;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the configuration that Raft persists locally on the filesystem.
 *
 * <p>In addition to protocol state managed through {@link RaftPersistentStorage}, we keep
 *
 * <ul>
 *   <li>partition id
 *   <li>directory path of the local data directory of the logstream used
 * </ul>
 */
public class RaftPersistentConfiguration {
  private final RaftConfigurationMetadata configuration = new RaftConfigurationMetadata();
  private static final ObjectWriter JSON_WRITER;
  private static final ObjectReader JSON_READER;

  static {
    final ObjectMapper mapper = new ObjectMapper();

    JSON_WRITER = mapper.writerFor(RaftConfigurationMetadata.class);
    JSON_READER = mapper.readerFor(RaftConfigurationMetadata.class);
  }

  private final File file;
  private final File tmpFile;
  private final Path path;
  private final Path tmpPath;

  private Integer votedFor;

  private final File logDirectory;
  private final File snapshotsDirectory;
  private final File statesDirectory;

  public RaftPersistentConfiguration(
      final File metaFile,
      final File partitionLogDir,
      final File partitionSnapshotsDir,
      final File statesDir) {
    this.logDirectory = partitionLogDir;
    this.snapshotsDirectory = partitionSnapshotsDir;
    this.statesDirectory = statesDir;
    file = metaFile;
    tmpFile = new File(file.getAbsolutePath() + ".tmp");
    path = Paths.get(file.getAbsolutePath());
    tmpPath = Paths.get(file.getAbsolutePath() + ".tmp");

    load();
  }

  public void delete() {
    FileUtil.deleteFile(file);
    FileUtil.deleteFile(tmpFile);
  }

  public List<Integer> getMembers() {
    return new ArrayList<>(configuration.getMembers());
  }

  public RaftPersistentConfiguration setMembers(final List<Integer> members) {
    members.forEach(this::addMember);
    return this;
  }

  public RaftPersistentConfiguration addMember(final int nodeId) {
    configuration.getMembers().add(nodeId);
    return this;
  }

  public RaftPersistentConfiguration removeMember(final int nodeId) {
    configuration.getMembers().removeIf(member -> member.equals(nodeId));
    return this;
  }

  public RaftPersistentConfiguration clearMembers() {
    configuration.getMembers().clear();

    return this;
  }

  private void load() {
    if (file.exists()) {
      final RaftConfigurationMetadata metadata;

      try (final InputStream is = new FileInputStream(file)) {
        metadata = JSON_READER.readValue(is);
      } catch (final IOException e) {
        throw new RuntimeException("Unable to read raft storage", e);
      }

      if (metadata != null) {
        configuration.copy(metadata);
      }
    }
  }

  public RaftPersistentConfiguration save() {
    try (final FileOutputStream os = new FileOutputStream(tmpFile)) {
      os.write(JSON_WRITER.writeValueAsBytes(configuration));
      os.flush();
    } catch (final IOException e) {
      throw new RuntimeException("Unable to write raft storage", e);
    }

    try {
      FileUtil.replace(tmpPath, path);
    } catch (final IOException e) {
      throw new RuntimeException("Unable to replace raft storage", e);
    }

    return this;
  }

  public int getPartitionId() {
    return configuration.getPartitionId();
  }

  public int getReplicationFactor() {
    return configuration.getReplicationFactor();
  }

  public File getLogDirectory() {
    return logDirectory;
  }

  public RaftPersistentConfiguration setPartitionId(final int partitionId) {
    configuration.setPartitionId(partitionId);
    return this;
  }

  public RaftPersistentConfiguration setReplicationFactor(final int replicationFactor) {
    configuration.setReplicationFactor(replicationFactor);
    return this;
  }

  public RaftPersistentConfiguration setLogSegmentSize(final long value) {
    configuration.setLogSegmentSize(value);
    return this;
  }

  public long getLogSegmentSize() {
    return configuration.getLogSegmentSize();
  }

  public File getSnapshotsDirectory() {
    return snapshotsDirectory;
  }

  public File getStatesDirectory() {
    return statesDirectory;
  }
}
