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
}
