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

import static io.zeebe.util.EnsureUtil.ensureNotNull;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.zeebe.raft.RaftPersistentStorage;
import io.zeebe.transport.SocketAddress;
import io.zeebe.util.buffer.BufferUtil;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

/**
 * Represents the configuration that Raft persists locally on the filesystem.
 *
 * <p>In addition to protocol state managed through {@link RaftPersistentStorage}, we keep
 *
 * <ul>
 *   <li>partition id
 *   <li>topic name
 *   <li>directory path of the local data directory of the logstream used
 * </ul>
 */
public class RaftPersistentConfiguration implements RaftPersistentStorage {
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

  private final SocketAddress votedFor = new SocketAddress();

  private final File logDirectory;
  private final File snapshotsDirectory;

  public RaftPersistentConfiguration(
      final File metaFile, File partitionLogDir, File partitionSnapshotsDir) {
    this.logDirectory = partitionLogDir;
    this.snapshotsDirectory = partitionSnapshotsDir;
    file = metaFile;
    tmpFile = new File(file.getAbsolutePath() + ".tmp");
    path = Paths.get(file.getAbsolutePath());
    tmpPath = Paths.get(file.getAbsolutePath() + ".tmp");

    load();
  }

  public void delete() {
    file.delete();
    tmpFile.delete();
  }

  @Override
  public int getTerm() {
    return configuration.getTerm();
  }

  @Override
  public RaftPersistentConfiguration setTerm(final int term) {
    configuration.setTerm(term);
    return this;
  }

  @Override
  public SocketAddress getVotedFor() {
    if (votedFor.hostLength() > 0) {
      return votedFor;
    } else {
      return null;
    }
  }

  @Override
  public RaftPersistentConfiguration setVotedFor(final SocketAddress votedFor) {
    if (votedFor != null) {
      configuration.setVotedForHost(votedFor.host());
      configuration.setVotedForPort(votedFor.port());
      this.votedFor.wrap(votedFor);
    } else {
      configuration.setVotedForHost("");
      configuration.setVotedForPort(0);
      this.votedFor.reset();
    }

    return this;
  }

  public List<SocketAddress> getMembers() {
    return configuration
        .getMembers()
        .stream()
        .map(member -> new SocketAddress(member.getHost(), member.getPort()))
        .collect(Collectors.toList());
  }

  public RaftPersistentConfiguration setMembers(List<SocketAddress> members) {
    members.forEach(this::addMember);
    return this;
  }

  @Override
  public RaftPersistentConfiguration addMember(final SocketAddress memberAddress) {
    ensureNotNull("Member address", memberAddress);
    final RaftConfigurationMetadataMember member =
        new RaftConfigurationMetadataMember(memberAddress.host(), memberAddress.port());
    configuration.getMembers().add(member);

    return this;
  }

  @Override
  public RaftPersistentStorage removeMember(final SocketAddress memberAddress) {
    ensureNotNull("Member address", memberAddress);

    configuration
        .getMembers()
        .removeIf(
            member ->
                member.getHost().equals(memberAddress.host())
                    && member.getPort() == memberAddress.port());
    return this;
  }

  @Override
  public RaftPersistentStorage clearMembers() {
    configuration.getMembers().clear();

    return this;
  }

  private void load() {
    if (file.exists()) {
      final RaftConfigurationMetadata metadata;

      try (InputStream is = new FileInputStream(file)) {
        metadata = JSON_READER.readValue(is);
      } catch (final IOException e) {
        throw new RuntimeException("Unable to read raft storage", e);
      }

      if (metadata != null) {
        configuration.copy(metadata);
        votedFor.host(configuration.getVotedForHost());
        votedFor.port(configuration.getVotedForPort());
      }
    }
  }

  @Override
  public RaftPersistentConfiguration save() {
    try (FileOutputStream os = new FileOutputStream(tmpFile)) {
      os.write(JSON_WRITER.writeValueAsBytes(configuration));
      os.flush();
    } catch (final IOException e) {
      throw new RuntimeException("Unable to write raft storage", e);
    }

    try {
      try {
        Files.move(tmpPath, path, ATOMIC_MOVE);
      } catch (final Exception e) {
        // failed with atomic move, lets try again with normal replace move
        Files.move(tmpPath, path, REPLACE_EXISTING);
      }
    } catch (final IOException e) {
      throw new RuntimeException("Unable to replace raft storage", e);
    }

    return this;
  }

  public DirectBuffer getTopicName() {
    return BufferUtil.wrapString(configuration.getTopicName());
  }

  public int getPartitionId() {
    return configuration.getPartitionId();
  }

  @Override
  public int getReplicationFactor() {
    return configuration.getReplicationFactor();
  }

  public File getLogDirectory() {
    return logDirectory;
  }

  public RaftPersistentConfiguration setTopicName(DirectBuffer topicName) {
    configuration.setTopicName(BufferUtil.bufferAsString(topicName));
    return this;
  }

  public RaftPersistentConfiguration setPartitionId(int partitionId) {
    configuration.setPartitionId(partitionId);
    return this;
  }

  public RaftPersistentConfiguration setReplicationFactor(int replicationFactor) {
    configuration.setReplicationFactor(replicationFactor);
    return this;
  }

  public RaftPersistentConfiguration setLogSegmentSize(long value) {
    configuration.setLogSegmentSize(value);
    return this;
  }

  public long getLogSegmentSize() {
    return configuration.getLogSegmentSize();
  }

  public File getSnapshotsDirectory() {
    return snapshotsDirectory;
  }
}
