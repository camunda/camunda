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
package io.camunda.zeebe.model.bpmn.instance.zeebe;

import io.camunda.zeebe.model.bpmn.instance.BpmnModelElementInstance;

public interface ZeebeCalledElement extends BpmnModelElementInstance {

  String getProcessId();

  void setProcessId(String processId);

  String getBusinessId();

  void setBusinessId(String businessId);

  /**
   * Returns whether the {@code businessId} attribute is present on the element, independently of
   * its value. A present-but-empty attribute ({@code businessId=""}) returns {@code true} here
   * while {@link #getBusinessId()} returns {@code null}, which lets callers distinguish "override
   * with no Business ID" (present) from "inherit the parent's Business ID" (absent).
   */
  boolean hasBusinessId();

  boolean isPropagateAllChildVariablesEnabled();

  void setPropagateAllChildVariablesEnabled(boolean propagateAllChildVariablesEnabled);

  boolean isPropagateAllParentVariablesEnabled();

  void setPropagateAllParentVariablesEnabled(boolean propagateAllParentVariablesEnabled);

  ZeebeBindingType getBindingType();

  void setBindingType(ZeebeBindingType bindingType);

  String getVersionTag();

  void setVersionTag(String versionTag);
}
