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
package io.camunda.process.test.api.assertions;

import io.camunda.client.api.search.filter.FlownodeInstanceFilter;
import io.camunda.client.api.search.response.FlowNodeInstance;

/** A collection of predefined {@link ElementSelector}s. */
public class ElementSelectors {

  /**
   * Select the BPMN element by its ID.
   *
   * @param elementId the ID of the BPMN element.
   * @return the selector
   */
  public static ElementSelector byId(final String elementId) {
    return new ElementIdSelector(elementId);
  }

  /**
   * Select the BPMN element by its name.
   *
   * @param elementName the name of the BPMN element.
   * @return the selector
   */
  public static ElementSelector byName(final String elementName) {
    return new ElementNameSelector(elementName);
  }

  private static final class ElementIdSelector implements ElementSelector {

    private final String elementId;

    private ElementIdSelector(final String elementId) {
      this.elementId = elementId;
    }

    @Override
    public boolean test(final FlowNodeInstance element) {
      return elementId.equals(element.getFlowNodeId());
    }

    @Override
    public String describe() {
      return elementId;
    }

    @Override
    public void applyFilter(final FlownodeInstanceFilter filter) {
      filter.flowNodeId(elementId);
    }
  }

  private static final class ElementNameSelector implements ElementSelector {

    private final String elementName;

    private ElementNameSelector(final String elementName) {
      this.elementName = elementName;
    }

    @Override
    public boolean test(final FlowNodeInstance element) {
      return elementName.equals(element.getFlowNodeName());
    }

    @Override
    public String describe() {
      return elementName;
    }
  }
}
