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

public class DateTimeFilterProperty {

  private String eq;
  private String neq;
  private Boolean exists;
  private String gt;
  private String gte;
  private String lt;
  private String lte;
  private List<String> in = new ArrayList<>();

  public String getEq() {
    return eq;
  }

  public void setEq(String eq) {
    this.eq = eq;
  }

  public String getNeq() {
    return neq;
  }

  public void setNeq(String neq) {
    this.neq = neq;
  }

  public Boolean getExists() {
    return exists;
  }

  public void setExists(Boolean exists) {
    this.exists = exists;
  }

  public String getGt() {
    return gt;
  }

  public void setGt(String gt) {
    this.gt = gt;
  }

  public String getGte() {
    return gte;
  }

  public void setGte(String gte) {
    this.gte = gte;
  }

  public String getLt() {
    return lt;
  }

  public void setLt(String lt) {
    this.lt = lt;
  }

  public String getLte() {
    return lte;
  }

  public void setLte(String lte) {
    this.lte = lte;
  }

  public List<String> getIn() {
    return in;
  }

  public void setIn(List<String> in) {
    this.in = in;
  }

  public static io.camunda.client.protocol.rest.DateTimeFilterProperty toProtocolObject(
      DateTimeFilterProperty object) {
    if (object == null) {
      return null;
    }

    final io.camunda.client.protocol.rest.DateTimeFilterProperty protocolObject =
        new io.camunda.client.protocol.rest.DateTimeFilterProperty();
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

  public static DateTimeFilterProperty fromProtocolObject(
      io.camunda.client.protocol.rest.DateTimeFilterProperty protocolObject) {
    if (protocolObject == null) {
      return null;
    }

    final DateTimeFilterProperty object = new DateTimeFilterProperty();
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
