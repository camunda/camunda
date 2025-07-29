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
package io.camunda.client.impl.command;

import io.camunda.client.api.JsonMapper;
import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1;
import io.camunda.client.api.command.CompleteAdHocSubProcessResultStep1.CompleteAdHocSubProcessResultStep2;
import io.camunda.client.api.command.enums.JobResultType;
import java.util.ArrayList;
import java.util.List;

public class CompleteAdHocSubProcessJobResultImpl
    extends CommandWithVariables<CompleteAdHocSubProcessResultStep2>
    implements CompleteAdHocSubProcessResultStep1, CompleteAdHocSubProcessResultStep2 {

  private final List<ActivateElement> activateElements = new ArrayList<>();
  private ActivateElement latestActivateElement;
  private boolean completionConditionFulfilled;
  private boolean cancelRemainingInstances;

  public CompleteAdHocSubProcessJobResultImpl(final JsonMapper jsonMapper) {
    super(jsonMapper);
  }

  public List<ActivateElement> getActivateElements() {
    return activateElements;
  }

  @Override
  public JobResultType getType() {
    return JobResultType.AD_HOC_SUB_PROCESS;
  }

  @Override
  public CompleteAdHocSubProcessResultStep2 activateElement(final String elementId) {
    ArgumentUtil.ensureNotNull("elementId", elementId);
    latestActivateElement = new ActivateElement().setElementId(elementId);
    activateElements.add(latestActivateElement);
    return this;
  }

  @Override
  public CompleteAdHocSubProcessResultStep1 completionConditionFulfilled(
      final boolean completionConditionFulfilled) {
    this.completionConditionFulfilled = completionConditionFulfilled;
    return this;
  }

  @Override
  public CompleteAdHocSubProcessResultStep1 cancelRemainingInstances(
      final boolean cancelRemainingInstances) {
    this.cancelRemainingInstances = cancelRemainingInstances;
    return this;
  }

  public boolean isCompletionConditionFulfilled() {
    return completionConditionFulfilled;
  }

  public boolean isCancelRemainingInstances() {
    return cancelRemainingInstances;
  }

  @Override
  protected CompleteAdHocSubProcessResultStep2 setVariablesInternal(final String variables) {
    latestActivateElement.setVariables(variables);
    return this;
  }

  public static class ActivateElement {
    private String elementId;
    private String variables;

    public String getElementId() {
      return elementId;
    }

    public ActivateElement setElementId(final String elementId) {
      this.elementId = elementId;
      return this;
    }

    public String getVariables() {
      return variables;
    }

    public ActivateElement setVariables(final String variables) {
      this.variables = variables;
      return this;
    }
  }
}
