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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.zeebe.broker.exporter.ExporterObjectMapper;

class ExporterMappedObject {

  protected static final ExporterObjectMapper OBJECT_MAPPER = new ExporterObjectMapper();

  @JsonIgnore private String json;

  public String toJson() {
    if (json != null) {
      return json;
    }

    return OBJECT_MAPPER.toJson(this);
  }

  public ExporterMappedObject setJson(String json) {
    this.json = json;
    return this;
  }

  public ExporterMappedObject setJson(Object object) {
    this.json = OBJECT_MAPPER.toJson(object);
    return this;
  }
}
