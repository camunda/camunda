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
package io.camunda.process.test.impl.spec.dsl.verifications;

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.ElementSelector;
import io.camunda.process.test.api.assertions.ElementSelectors;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.impl.spec.SpecTestContext;
import io.camunda.process.test.impl.spec.dsl.AbstractSpecInstruction;
import io.camunda.process.test.impl.spec.dsl.SpecVerification;

public class ElementInstanceStateVerification extends AbstractSpecInstruction
    implements SpecVerification {

  private String processInstanceAlias;
  private String elementId = null;
  private String elementName = null;
  private ElementInstanceState state;

  public String getProcessInstanceAlias() {
    return processInstanceAlias;
  }

  public void setProcessInstanceAlias(final String processInstanceAlias) {
    this.processInstanceAlias = processInstanceAlias;
  }

  public String getElementId() {
    return elementId;
  }

  public void setElementId(final String elementId) {
    this.elementId = elementId;
  }

  public String getElementName() {
    return elementName;
  }

  public void setElementName(final String elementName) {
    this.elementName = elementName;
  }

  public ElementInstanceState getState() {
    return state;
  }

  public void setState(final ElementInstanceState state) {
    this.state = state;
  }

  @Override
  public void verify(
      final SpecTestContext testContext, final CamundaProcessTestContext processTestContext)
      throws AssertionError {

    final ElementSelector elementSelector = getElementSelector();

    final ProcessInstanceAssert processInstanceAssert =
        testContext.assertThatProcessInstance(processInstanceAlias);

    switch (state) {
      case ACTIVE:
        processInstanceAssert.hasActiveElements(elementSelector);
        break;

      case COMPLETED:
        processInstanceAssert.hasCompletedElements(elementSelector);
        break;

      case TERMINATED:
        processInstanceAssert.hasTerminatedElements(elementSelector);
        break;

      default:
    }
  }

  private ElementSelector getElementSelector() {
    if (elementId != null) {
      return ElementSelectors.byId(elementId);

    } else if (elementName != null) {
      return ElementSelectors.byName(elementName);

    } else {
      throw new IllegalArgumentException("Must define either an element ID or an element name");
    }
  }
}
