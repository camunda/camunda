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
package io.zeebe.exporter.api.record;

import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.Intent;

/** Encapsulates metadata information shared by all records. */
public interface RecordMetadata {
  /** @return the intent of the record */
  Intent getIntent();

  /** @return the partition ID on which the record was published */
  int getPartitionId();

  /** @return the type of the record (event, command or command rejection) */
  RecordType getRecordType();

  /**
   * @return the type of rejection if {@link #getRecordType()} returns {@link
   *     io.zeebe.protocol.clientapi.RecordType#COMMAND_REJECTION} or else <code>null</code>.
   */
  RejectionType getRejectionType();

  /**
   * @return the reason why a command was rejected if {@link #getRecordType()} returns {@link
   *     io.zeebe.protocol.clientapi.RecordType#COMMAND_REJECTION} or else <code>null</code>.
   */
  String getRejectionReason();

  /** @return the type of the record (e.g. job, workflow, workflow instance, etc.) */
  ValueType getValueType();

  /** @return a JSON marshaled representation of the record metadata */
  String toJson();
}
