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
package io.zeebe.raft.event;

import io.zeebe.logstreams.log.LogStreamRecordWriter;
import io.zeebe.logstreams.log.LogStreamWriterImpl;
import io.zeebe.msgpack.value.ValueArray;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.raft.Raft;
import io.zeebe.raft.RaftMember;
import java.util.List;

public class RaftEvent {
  public final LogStreamRecordWriter logStreamWriter = new LogStreamWriterImpl();
  public final RecordMetadata metadata = new RecordMetadata();
  public final RaftConfigurationEvent configuration = new RaftConfigurationEvent();

  public RaftIntent intent;

  public RaftEvent reset() {
    logStreamWriter.reset();
    metadata.reset();
    configuration.reset();

    return this;
  }

  public RaftEvent setIntent(final RaftIntent intent) {
    this.intent = intent;
    return this;
  }

  public long tryWrite(final Raft raft) {
    logStreamWriter.wrap(raft.getLogStream());

    metadata.reset().valueType(ValueType.RAFT).recordType(RecordType.EVENT).intent(intent);

    configuration.reset();

    final ValueArray<RaftConfigurationEventMember> configurationMembers = configuration.members();

    // add self also to configuration
    configurationMembers.add().setNodeId(raft.getNodeId());

    final List<RaftMember> memberList = raft.getRaftMembers().getMemberList();
    for (final RaftMember member : memberList) {
      configurationMembers.add().setNodeId(member.getNodeId());
    }

    return logStreamWriter
        .positionAsKey()
        .metadataWriter(metadata)
        .valueWriter(configuration)
        .tryWrite();
  }
}
