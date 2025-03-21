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
package io.camunda.client.wrappers;

import java.util.ArrayList;
import java.util.List;

public class ProcessInstanceVariableFilterRequest {

  private String name;
  private StringFilterProperty value;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public StringFilterProperty getValue() {
    return value;
  }

  public void setValue(StringFilterProperty value) {
    this.value = value;
  }

  public static io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest
      toProtocolObject(ProcessInstanceVariableFilterRequest object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest protocolObject =
        new io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest();
    protocolObject.setName(object.name);
    protocolObject.setValue(StringFilterProperty.toProtocolObject(object.value));
    return protocolObject;
  }

  public static List<io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest>
      toProtocolList(List<ProcessInstanceVariableFilterRequest> list) {
    if (list == null) {
      return null;
    }

    final List<io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest> protocolList =
        new ArrayList<>();
    list.forEach(item -> protocolList.add(toProtocolObject(item)));
    return protocolList;
  }

  public static ProcessInstanceVariableFilterRequest fromProtocolObject(
      io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final ProcessInstanceVariableFilterRequest object = new ProcessInstanceVariableFilterRequest();
    object.name = protocolObject.getName();
    object.value = StringFilterProperty.fromProtocolObject(protocolObject.getValue());
    return object;
  }

  public static List<ProcessInstanceVariableFilterRequest> fromProtocolList(
      List<io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest> protocolList) {
    if (protocolList == null) {
      return null;
    }

    final List<ProcessInstanceVariableFilterRequest> list = new ArrayList<>();
    protocolList.forEach(item -> list.add(fromProtocolObject(item)));
    return list;
  }
}
