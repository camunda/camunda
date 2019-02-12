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
package io.zeebe.test.exporter.record;

import io.zeebe.exporter.record.RecordValueWithPayload;
import java.util.Map;

public class MockRecordValueWithPayload extends MockRecordValue implements RecordValueWithPayload {

  private Map<String, Object> payload;

  public MockRecordValueWithPayload() {}

  public MockRecordValueWithPayload(Map<String, Object> payload) {
    this.payload = payload;
  }

  @Override
  public String getPayload() {
    if (payload != null) {
      return OBJECT_MAPPER.toJson(payload);
    }

    return null;
  }

  public MockRecordValueWithPayload setPayload(String payloadAsJson) {
    this.payload = OBJECT_MAPPER.fromJsonAsMap(payloadAsJson);
    return this;
  }

  public MockRecordValueWithPayload setPayload(Map<String, Object> payload) {
    this.payload = payload;
    return this;
  }

  @Override
  public Map<String, Object> getPayloadAsMap() {
    return payload;
  }
}
