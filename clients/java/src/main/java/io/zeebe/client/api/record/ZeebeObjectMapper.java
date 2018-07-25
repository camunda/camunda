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
package io.zeebe.client.api.record;

import io.zeebe.client.cmd.ClientException;

/** (De-) Serialize records from/to JSON. */
public interface ZeebeObjectMapper {

  /**
   * Serializes the given record to JSON.
   *
   * <pre>
   * String json = zeebeClient
   *  .objectMapper()
   *  .toJson(jobEvent);
   * </pre>
   *
   * @param record the record to serialize
   * @return a canonical JSON representation of the record. This representation (without
   *     modifications) can be used to de-serialize the event via {@link #fromJson(String, Class)}.
   * @throws ClientException if fail to serialize to JSON
   */
  String toJson(Record record);

  /**
   * De-serializes an record from JSON.
   *
   * <pre>
   * JobEvent job = zeebeClient
   *  .objectMapper()
   *  .fromJson(json, JobEvent.class);
   * </pre>
   *
   * @param <T> the type of record
   * @param json the JSON value to de-serialize
   * @param recordClass the type of record to de-serialize it to. Must match the records's entity
   *     type.
   * @return the de-serialized record
   * @throws ClientException if fail to de-serialize from JSON, or the record type doesn't match
   */
  <T extends Record> T fromJson(String json, Class<T> recordClass);
}
