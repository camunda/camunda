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

import io.zeebe.exporter.api.record.RecordValueWithVariables;
import java.util.Map;

public class MockRecordValueWithVariables extends MockRecordValue
    implements RecordValueWithVariables {

  private Map<String, Object> variables;

  public MockRecordValueWithVariables() {}

  public MockRecordValueWithVariables(Map<String, Object> variables) {
    this.variables = variables;
  }

  @Override
  public String getVariables() {
    if (variables != null) {
      return OBJECT_MAPPER.toJson(variables);
    }

    return null;
  }

  public MockRecordValueWithVariables setVariables(String variablesAsJson) {
    this.variables = OBJECT_MAPPER.fromJsonAsMap(variablesAsJson);
    return this;
  }

  public MockRecordValueWithVariables setVariables(Map<String, Object> variables) {
    this.variables = variables;
    return this;
  }

  @Override
  public Map<String, Object> getVariablesAsMap() {
    return variables;
  }
}
