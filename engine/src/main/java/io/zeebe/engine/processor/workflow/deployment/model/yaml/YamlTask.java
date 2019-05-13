/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow.deployment.model.yaml;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class YamlTask {
  private String id = "";

  private String type = "";
  private int retries = ZeebeTaskDefinition.DEFAULT_RETRIES;

  private Map<String, String> headers = new HashMap<>();

  private List<YamlMapping> inputs = new ArrayList<>();
  private List<YamlMapping> outputs = new ArrayList<>();

  private boolean end = false;

  @JsonProperty("goto")
  private String next;

  @JsonProperty("switch")
  private List<YamlCase> cases = new ArrayList<>();

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public int getRetries() {
    return retries;
  }

  public void setRetries(int retries) {
    this.retries = retries;
  }

  public Map<String, String> getHeaders() {
    return headers;
  }

  public void setHeaders(Map<String, String> headers) {
    this.headers = headers;
  }

  public List<YamlMapping> getInputs() {
    return inputs;
  }

  public void setInputs(List<YamlMapping> inputs) {
    this.inputs = inputs;
  }

  public List<YamlMapping> getOutputs() {
    return outputs;
  }

  public void setOutputs(List<YamlMapping> outputs) {
    this.outputs = outputs;
  }

  public List<YamlCase> getCases() {
    return cases;
  }

  public void setCases(List<YamlCase> cases) {
    this.cases = cases;
  }

  public String getNext() {
    return next;
  }

  public void setNext(String next) {
    this.next = next;
  }

  public boolean isEnd() {
    return end;
  }

  public void setEnd(boolean end) {
    this.end = end;
  }
}
