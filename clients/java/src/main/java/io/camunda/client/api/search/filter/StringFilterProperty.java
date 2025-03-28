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
package io.camunda.client.api.search.filter;

import java.util.ArrayList;
import java.util.List;

public class StringFilterProperty {

  private String eq;
  private String neq;
  private Boolean exists;
  private List<String> in = new ArrayList<>();
  private String like;

  public String getEq() {
    return eq;
  }

  public StringFilterProperty setEq(String eq) {
    this.eq = eq;
    return this;
  }

  public String getNeq() {
    return neq;
  }

  public StringFilterProperty setNeq(String neq) {
    this.neq = neq;
    return this;
  }

  public Boolean getExists() {
    return exists;
  }

  public StringFilterProperty setExists(Boolean exists) {
    this.exists = exists;
    return this;
  }

  public List<String> getIn() {
    return in;
  }

  public StringFilterProperty setIn(List<String> in) {
    this.in = in;
    return this;
  }

  public StringFilterProperty addInItem(String item) {
    this.in.add(item);
    return this;
  }

  public String getLike() {
    return like;
  }

  public StringFilterProperty setLike(String like) {
    this.like = like;
    return this;
  }
}
