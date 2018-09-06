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
package io.zeebe.test.util.record;

import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.RaftRecordValue;
import io.zeebe.exporter.record.value.raft.RaftMember;
import java.util.List;
import java.util.stream.Stream;

public class RaftRecordStream extends ExporterRecordStream<RaftRecordValue, RaftRecordStream> {

  public RaftRecordStream(final Stream<Record<RaftRecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected RaftRecordStream supply(final Stream<Record<RaftRecordValue>> wrappedStream) {
    return new RaftRecordStream(wrappedStream);
  }

  public RaftRecordStream withMembers(final List<RaftMember> members) {
    return valueFilter(v -> members.equals(v.getMembers()));
  }

  public RaftRecordStream withMember(final RaftMember member) {
    return valueFilter(v -> v.getMembers().contains(member));
  }
}
