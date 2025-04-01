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

import io.camunda.client.api.search.enums.ProcessInstanceState;
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
}
