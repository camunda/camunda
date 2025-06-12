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
package io.camunda.process.test.api.coverage.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class Model {

  /** Key of the model. */
  private final String processDefinitionId;

  /** Total count of executable elements. */
  private final int totalElementCount;

  /** Version of the model. */
  private final String version;

  /** XML representation of the model. */
  private final String xml;

  @JsonCreator
  public Model(
      @JsonProperty("processDefinitionId") final String processDefinitionId,
      @JsonProperty("totalElementCount") final int totalElementCount,
      @JsonProperty("version") final String version,
      @JsonProperty("xml") final String xml) {
    this.processDefinitionId = processDefinitionId;
    this.totalElementCount = totalElementCount;
    this.version = version;
    this.xml = xml;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public String getVersion() {
    return version;
  }

  public int getTotalElementCount() {
    return totalElementCount;
  }

  public String getXml() {
    return xml;
  }
}
