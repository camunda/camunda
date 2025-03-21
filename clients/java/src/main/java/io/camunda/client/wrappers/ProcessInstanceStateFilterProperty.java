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

public class ProcessInstanceStateFilterProperty {

  private ProcessInstanceState eq;
  private ProcessInstanceState neq;
  private Boolean exists;
  private List<ProcessInstanceState> in = new ArrayList<>();
  private String like;

  public ProcessInstanceState getEq() {
    return eq;
  }

  public void setEq(ProcessInstanceState eq) {
    this.eq = eq;
  }

  public ProcessInstanceState getNeq() {
    return neq;
  }

  public void setNeq(ProcessInstanceState neq) {
    this.neq = neq;
  }

  public Boolean getExists() {
    return exists;
  }

  public void setExists(Boolean exists) {
    this.exists = exists;
  }

  public List<ProcessInstanceState> getIn() {
    return in;
  }

  public void setIn(List<ProcessInstanceState> in) {
    this.in = in;
  }

  public String getLike() {
    return like;
  }

  public void setLike(String like) {
    this.like = like;
  }

  public static io.camunda.client.protocol.rest.ProcessInstanceStateFilterProperty toProtocolObject(
      ProcessInstanceStateFilterProperty object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.ProcessInstanceStateFilterProperty protocolObject =
        new io.camunda.client.protocol.rest.ProcessInstanceStateFilterProperty();
    protocolObject.set$Eq(ProcessInstanceState.toProtocolEnum(object.eq));
    protocolObject.set$Exists(object.exists);
    if (object.in == null) {
      protocolObject.set$In(null);
    } else {
      protocolObject.set$In(new ArrayList<>());
      object.in.forEach(
          item -> protocolObject.add$InItem(ProcessInstanceState.toProtocolEnum(item)));
    }
    protocolObject.set$Like(object.like);
    protocolObject.set$Neq(ProcessInstanceState.toProtocolEnum(object.neq));

    return protocolObject;
  }

  public static ProcessInstanceStateFilterProperty fromProtocolObject(
      io.camunda.client.protocol.rest.ProcessInstanceStateFilterProperty protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final ProcessInstanceStateFilterProperty object = new ProcessInstanceStateFilterProperty();
    object.eq = ProcessInstanceState.fromProtocolEnum(protocolObject.get$Eq());
    object.exists = protocolObject.get$Exists();
    if (protocolObject.get$In() == null) {
      object.in = null;
    } else {
      object.in = new ArrayList<>();
      protocolObject
          .get$In()
          .forEach(item -> object.in.add(ProcessInstanceState.fromProtocolEnum(item)));
    }
    object.like = protocolObject.get$Like();
    object.neq = ProcessInstanceState.fromProtocolEnum(protocolObject.get$Neq());

    return object;
  }
}
