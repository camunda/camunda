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
package io.zeebe.test.broker.protocol.clientapi;

import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.test.util.stream.StreamWrapper;
import java.util.stream.Stream;

public class SubscribedRecordStream extends StreamWrapper<SubscribedRecord> {

  public SubscribedRecordStream(Stream<SubscribedRecord> wrappedStream) {
    super(wrappedStream);
  }

  private SubscribedRecordStream recordsOfValueType(ValueType valueType) {
    return new SubscribedRecordStream(filter(r -> r.valueType() == valueType));
  }

  private SubscribedRecordStream recordsOfType(RecordType recordType) {
    return new SubscribedRecordStream(filter(r -> r.recordType() == recordType));
  }

  public SubscribedRecordStream onlyEvents() {
    return recordsOfType(RecordType.EVENT);
  }

  public SubscribedRecordStream onlyCommands() {
    return recordsOfType(RecordType.COMMAND);
  }

  public SubscribedRecordStream ofTypeWorkflowInstance() {
    return recordsOfValueType(ValueType.WORKFLOW_INSTANCE);
  }

  public SubscribedRecordStream ofTypeJob() {
    return recordsOfValueType(ValueType.JOB);
  }

  public SubscribedRecordStream ofTypeIncident() {
    return recordsOfValueType(ValueType.INCIDENT);
  }

  public SubscribedRecordStream onlyRejections() {
    return recordsOfType(RecordType.COMMAND_REJECTION);
  }

  public SubscribedRecordStream withIntent(Intent intent) {
    return new SubscribedRecordStream(filter(r -> r.intent() == intent));
  }

  public SubscribedRecord getFirst() {
    return findFirst().orElseThrow(() -> new AssertionError("no event received"));
  }
}
