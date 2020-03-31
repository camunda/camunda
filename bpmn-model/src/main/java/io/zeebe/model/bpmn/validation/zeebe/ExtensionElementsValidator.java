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
package io.zeebe.model.bpmn.validation.zeebe;

import io.zeebe.model.bpmn.instance.BaseElement;
import io.zeebe.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public final class ExtensionElementsValidator<T extends BaseElement, E extends ModelElementInstance>
    implements ModelElementValidator<T> {

  private final Class<T> elementType;
  private final Class<E> extensionElement;
  private final String extensionElementName;

  private ExtensionElementsValidator(
      final Class<T> elementType,
      final Class<E> extensionElement,
      final String extensionElementName) {
    this.elementType = elementType;
    this.extensionElement = extensionElement;
    this.extensionElementName = extensionElementName;
  }

  @Override
  public Class<T> getElementType() {
    return elementType;
  }

  @Override
  public void validate(final T element, final ValidationResultCollector validationResultCollector) {

    final ExtensionElements extensionElements = element.getExtensionElements();

    if (extensionElements == null
        || extensionElements.getChildElementsByType(extensionElement).size() != 1) {

      validationResultCollector.addError(
          0,
          String.format(
              "Must have exactly one 'zeebe:%s' extension element", extensionElementName));
    }
  }

  public static <T extends BaseElement> Builder<T> verifyThat(final Class<T> elementType) {
    return new Builder<>(elementType);
  }

  public static class Builder<T extends BaseElement> {

    private final Class<T> elementType;

    public Builder(final Class<T> elementType) {
      this.elementType = elementType;
    }

    public <E extends ModelElementInstance>
        ExtensionElementsValidator<T, E> hasSingleExtensionElement(
            final Class<E> extensionElement, final String name) {

      return new ExtensionElementsValidator<>(elementType, extensionElement, name);
    }
  }
}
