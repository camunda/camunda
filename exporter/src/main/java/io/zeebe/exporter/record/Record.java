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
package io.zeebe.exporter.record;

/** Represents a record published to the log stream. */
public interface Record<T extends RecordValue> {

  /**
   * Retrieves relevant metadata of the record, such as the type of the value ({@link
   * io.zeebe.protocol.clientapi.ValueType}), the type of record ({@link
   * io.zeebe.protocol.clientapi.RecordType}), etc.
   *
   * @return record metadata
   */
  RecordMetadata getMetadata();

  /**
   * Returns the raw value of the record, which should implement one of the interfaces in the {@link
   * io.zeebe.exporter.record.value} package.
   *
   * <p>The record value is essentially the record specific data, e.g. for a workflow instance
   * creation event, it would contain information relevant to the workflow instance being created.
   *
   * @return record value
   */
  T getValue();

  /** @return a JSON marshaled representation of this record */
  String toJson();
}
