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

import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.filter.ElementInstanceFilter;
import io.camunda.client.api.search.response.ElementInstance;

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

  /**
   * Select the BPMN element by its type.
   *
   * @param elementType the type of the BPMN element.
   * @return the selector
   */
  public static ElementSelector byElementType(final ElementInstanceType elementType) {
    return new ElementTypeSelector(elementType);
  }

  /**
   * Select the BPMN element by its element instance key.
   *
   * @param elementInstanceKey the key of the element instance.
   * @return the selector
   */
  public static ElementSelector byElementInstanceKey(final long elementInstanceKey) {
    return new ElementInstanceKeySelector(elementInstanceKey);
  }

  /**
   * Select the BPMN element by its state.
   *
   * @param state the state of the element instance.
   * @return the selector
   */
  public static ElementSelector byState(final ElementInstanceState state) {
    return new ElementStateSelector(state);
  }

  private static final class ElementIdSelector implements ElementSelector {

    private final String elementId;

    private ElementIdSelector(final String elementId) {
      this.elementId = elementId;
    }

    @Override
    public boolean test(final ElementInstance element) {
      return elementId.equals(element.getElementId());
    }

    @Override
    public String describe() {
      return elementId;
    }

    @Override
    public void applyFilter(final ElementInstanceFilter filter) {
      filter.elementId(elementId);
    }
  }

  private static final class ElementNameSelector implements ElementSelector {

    private final String elementName;

    private ElementNameSelector(final String elementName) {
      this.elementName = elementName;
    }

    @Override
    public boolean test(final ElementInstance element) {
      return elementName.equals(element.getElementName());
    }

    @Override
    public String describe() {
      return elementName;
    }

    @Override
    public void applyFilter(final ElementInstanceFilter filter) {
      filter.elementName(elementName);
    }
  }

  private static final class ElementTypeSelector implements ElementSelector {

    private final ElementInstanceType elementType;

    private ElementTypeSelector(final ElementInstanceType elementType) {
      this.elementType = elementType;
    }

    @Override
    public boolean test(final ElementInstance element) {
      return elementType.equals(element.getType());
    }

    @Override
    public String describe() {
      return "type: " + elementType.name();
    }

    @Override
    public void applyFilter(final ElementInstanceFilter filter) {
      filter.type(elementType);
    }
  }

  private static final class ElementInstanceKeySelector implements ElementSelector {

    private final long elementInstanceKey;

    private ElementInstanceKeySelector(final long elementInstanceKey) {
      this.elementInstanceKey = elementInstanceKey;
    }

    @Override
    public boolean test(final ElementInstance element) {
      return elementInstanceKey == element.getElementInstanceKey();
    }

    @Override
    public String describe() {
      return "element instance key: " + String.valueOf(elementInstanceKey);
    }

    @Override
    public void applyFilter(final ElementInstanceFilter filter) {
      filter.elementInstanceKey(elementInstanceKey);
    }
  }

  private static final class ElementStateSelector implements ElementSelector {

    private final ElementInstanceState state;

    private ElementStateSelector(final ElementInstanceState state) {
      this.state = state;
    }

    @Override
    public boolean test(final ElementInstance element) {
      return state.equals(element.getState());
    }

    @Override
    public String describe() {
      return "state: " + state.name();
    }

    @Override
    public void applyFilter(final ElementInstanceFilter filter) {
      filter.state(state);
    }
  }
}
