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

import io.camunda.zeebe.model.bpmn.impl.ZeebeConstants;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class ZeebeBindingTypeValidator<T extends ModelElementInstance>
    implements ModelElementValidator<T> {

  private static final List<String> ALLOWED_BINDING_TYPES =
      Arrays.stream(ZeebeBindingType.values())
          .map(ZeebeBindingType::toString)
          .collect(Collectors.toList());

  private final Class<T> elementType;

  public ZeebeBindingTypeValidator(final Class<T> elementType) {
    this.elementType = elementType;
  }

  @Override
  public Class<T> getElementType() {
    return elementType;
  }

  @Override
  public void validate(final T element, final ValidationResultCollector validationResultCollector) {
    final String bindingType = element.getAttributeValue(ZeebeConstants.ATTRIBUTE_BINDING_TYPE);
    final String versionTag = element.getAttributeValue(ZeebeConstants.ATTRIBUTE_VERSION_TAG);
    checkValidBindingTypeValue(validationResultCollector, bindingType);
    checkValidBindingTypeAndVersionTag(validationResultCollector, bindingType, versionTag);
  }

  private static void checkValidBindingTypeValue(
      final ValidationResultCollector validationResultCollector, final String bindingType) {
    if (bindingType != null && !ALLOWED_BINDING_TYPES.contains(bindingType)) {
      final String message =
          String.format(
              "Attribute '%s' must be one of: %s",
              ZeebeConstants.ATTRIBUTE_BINDING_TYPE, String.join(", ", ALLOWED_BINDING_TYPES));
      validationResultCollector.addError(0, message);
    }
  }

  private static void checkValidBindingTypeAndVersionTag(
      final ValidationResultCollector validationResultCollector,
      final String bindingType,
      final String versionTag) {
    if (ZeebeBindingType.versionTag.name().equals(bindingType) && isBlank(versionTag)) {
      validationResultCollector.addError(
          0,
          String.format(
              "Attribute '%s' must be present and not empty if '%s' is '%s'",
              ZeebeConstants.ATTRIBUTE_VERSION_TAG,
              ZeebeConstants.ATTRIBUTE_BINDING_TYPE,
              ZeebeBindingType.versionTag));
    } else if (!ZeebeBindingType.versionTag.name().equals(bindingType) && !isBlank(versionTag)) {
      validationResultCollector.addError(
          0,
          String.format(
              "Attribute '%s' may only be used if '%s' is '%s'",
              ZeebeConstants.ATTRIBUTE_VERSION_TAG,
              ZeebeConstants.ATTRIBUTE_BINDING_TYPE,
              ZeebeBindingType.versionTag));
    }
  }

  private static boolean isBlank(final String value) {
    return value == null || value.trim().isEmpty();
  }
}
