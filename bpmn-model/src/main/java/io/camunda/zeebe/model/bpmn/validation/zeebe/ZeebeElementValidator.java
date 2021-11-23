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
package io.camunda.zeebe.model.bpmn.validation.zeebe;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public final class ZeebeElementValidator<T extends ModelElementInstance>
    implements ModelElementValidator<T> {

  private final Class<T> elementType;
  private final List<AttributeAssertion<T>> attributeAssertions = new ArrayList<>();

  private ZeebeElementValidator(final Class<T> elementType) {
    this.elementType = elementType;
  }

  @Override
  public Class<T> getElementType() {
    return elementType;
  }

  @Override
  public void validate(final T element, final ValidationResultCollector validationResultCollector) {

    attributeAssertions.forEach(
        assertions -> {
          final String attributeValue = assertions.attributeSupplier.apply(element);
          if (attributeValue == null || attributeValue.isEmpty()) {
            validationResultCollector.addError(
                0,
                String.format(
                    "Attribute '%s' must be present and not empty", assertions.attributeName));
          }
        });
  }

  public ZeebeElementValidator<T> hasNonEmptyAttribute(
      final Function<T, String> attributeSupplier, final String attributeName) {
    attributeAssertions.add(new AttributeAssertion<>(attributeSupplier, attributeName));
    return this;
  }

  public static <T extends ModelElementInstance> ZeebeElementValidator<T> verifyThat(
      final Class<T> elementType) {
    return new ZeebeElementValidator<>(elementType);
  }

  private static final class AttributeAssertion<T extends ModelElementInstance> {
    private final Function<T, String> attributeSupplier;
    private final String attributeName;

    private AttributeAssertion(
        final Function<T, String> attributeSupplier, final String attributeName) {
      this.attributeSupplier = attributeSupplier;
      this.attributeName = attributeName;
    }
  }
}
