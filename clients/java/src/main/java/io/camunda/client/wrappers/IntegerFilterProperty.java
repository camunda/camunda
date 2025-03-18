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

public class IntegerFilterProperty {

  private Integer eq;
  private Integer neq;
  private Boolean exists;
  private Integer gt;
  private Integer gte;
  private Integer lt;
  private Integer lte;
  private List<Integer> in = new ArrayList<>();

  public Integer getEq() {
    return eq;
  }

  public void setEq(Integer eq) {
    this.eq = eq;
  }

  public Integer getNeq() {
    return neq;
  }

  public void setNeq(Integer neq) {
    this.neq = neq;
  }

  public Boolean getExists() {
    return exists;
  }

  public void setExists(Boolean exists) {
    this.exists = exists;
  }

  public Integer getGt() {
    return gt;
  }

  public void setGt(Integer gt) {
    this.gt = gt;
  }

  public Integer getGte() {
    return gte;
  }

  public void setGte(Integer gte) {
    this.gte = gte;
  }

  public Integer getLt() {
    return lt;
  }

  public void setLt(Integer lt) {
    this.lt = lt;
  }

  public Integer getLte() {
    return lte;
  }

  public void setLte(Integer lte) {
    this.lte = lte;
  }

  public List<Integer> getIn() {
    return in;
  }

  public void setIn(List<Integer> in) {
    this.in = in;
  }

  public static io.camunda.client.protocol.rest.IntegerFilterProperty toProtocolObject(
      IntegerFilterProperty object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.IntegerFilterProperty protocolObject =
        new io.camunda.client.protocol.rest.IntegerFilterProperty();
    protocolObject.set$Eq(object.eq);
    protocolObject.set$Neq(object.neq);
    protocolObject.set$Exists(object.exists);
    protocolObject.set$Gt(object.gt);
    protocolObject.set$Gte(object.gte);
    protocolObject.set$Lt(object.getLt());
    protocolObject.set$Lte(object.getLte());
    if (object.in == null) {
      protocolObject.set$In(null);
    } else {
      protocolObject.set$In(new ArrayList<>());
      object.in.forEach(protocolObject::add$InItem);
    }

    return protocolObject;
  }

  public static IntegerFilterProperty fromProtocolObject(
      io.camunda.client.protocol.rest.IntegerFilterProperty protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final IntegerFilterProperty object = new IntegerFilterProperty();
    object.eq = protocolObject.get$Eq();
    object.neq = protocolObject.get$Neq();
    object.exists = protocolObject.get$Exists();
    object.gt = protocolObject.get$Gt();
    object.gte = protocolObject.get$Gte();
    object.lt = protocolObject.get$Lt();
    object.lte = protocolObject.get$Lte();
    if (protocolObject.get$In() == null) {
      object.in = null;
    } else {
      object.in = new ArrayList<>();
      object.in.addAll(protocolObject.get$In());
    }
    return object;
  }
}
