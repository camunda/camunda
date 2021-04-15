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
package io.atomix.raft.roles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.raft.impl.RaftContext;
import io.atomix.raft.impl.zeebe.LogCompactor;
import io.atomix.raft.metrics.RaftReplicationMetrics;
import io.atomix.raft.protocol.AppendRequest;
import io.atomix.raft.protocol.AppendResponse;
import io.atomix.raft.storage.log.RaftLog;
import io.atomix.raft.storage.log.RaftLogWriter;
import io.atomix.raft.storage.log.entry.RaftLogEntry;
import io.atomix.raft.zeebe.ZeebeEntry;
import io.atomix.storage.StorageException.OutOfDiskSpace;
import io.atomix.storage.journal.Indexed;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

public class PassiveRoleTest {

  @Rule public Timeout timeout = new Timeout(30, TimeUnit.SECONDS);
  private PassiveRole role;
  private RaftLogWriter writer;

  @Before
  public void setup() throws IOException {
    writer = mock(RaftLogWriter.class);
    when(writer.getNextIndex()).thenReturn(1L);

    final RaftLog log = mock(RaftLog.class);
    when(log.shouldFlushExplicitly()).thenReturn(true);
    when(log.writer()).thenReturn(writer);

    final RaftContext ctx = mock(RaftContext.class);
    when(ctx.getLog()).thenReturn(log);
    when(ctx.getLogWriter()).thenReturn(writer);
    when(ctx.getTerm()).thenReturn(1L);
    when(ctx.getReplicationMetrics()).thenReturn(mock(RaftReplicationMetrics.class));
    when(ctx.getLogCompactor()).thenReturn(mock(LogCompactor.class));

    role = new PassiveRole(ctx);
  }

  @Test
  public void shouldFlushAfterAppendRequest() {
    // given
    final List<RaftLogEntry> entries =
        List.of(
            new ZeebeEntry(1, 1, 1, 12345, ByteBuffer.allocate(1)),
            new ZeebeEntry(1, 2, 2, 1, ByteBuffer.allocate(1)));
    final AppendRequest request = new AppendRequest(1, "", 0, 0, entries, 2);

    when(writer.append(any(RaftLogEntry.class)))
        .thenReturn(mock(Indexed.class))
        .thenReturn(mock(Indexed.class));

    // when
    final AppendResponse response = role.handleAppend(request).join();

    // then
    verify(writer).flush();
    assertThat(response.lastLogIndex()).isEqualTo(2);
  }

  @Test
  public void shouldFlushAfterPartiallyAppendedRequest() {
    // given
    final List<RaftLogEntry> entries =
        List.of(
            new ZeebeEntry(1, 1, 1, 1, ByteBuffer.allocate(1)),
            new ZeebeEntry(1, 2, 2, 1, ByteBuffer.allocate(1)));
    final AppendRequest request = new AppendRequest(1, "", 0, 0, entries, 2);

    when(writer.append(any(RaftLogEntry.class)))
        .thenReturn(mock(Indexed.class))
        .thenThrow(new OutOfDiskSpace("expected"));

    // when
    final AppendResponse response = role.handleAppend(request).join();

    // then
    verify(writer).flush();
    assertThat(response.lastLogIndex()).isEqualTo(1);
  }

  @Test
  public void shouldNotFlushIfNoEntryIsAppended() {
    // given
    final List<RaftLogEntry> entries = List.of(new ZeebeEntry(1, 1, 1, 1, ByteBuffer.allocate(1)));
    final AppendRequest request = new AppendRequest(1, "", 0, 0, entries, 2);

    when(writer.append(any(RaftLogEntry.class))).thenThrow(new OutOfDiskSpace("expected"));

    // when
    final AppendResponse response = role.handleAppend(request).join();

    // then
    verify(writer, never()).flush();
    assertThat(response.lastLogIndex()).isEqualTo(0);
  }
}
