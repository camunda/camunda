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

package io.camunda.zeebe.model.bpmn.builder;

import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperty;

public class ZeebePropertiesBuilderImpl<B extends AbstractBaseElementBuilder<?, ?>>
    implements ZeebePropertiesBuilder<B> {

  private final B elementBuilder;

  public ZeebePropertiesBuilderImpl(final B elementBuilder) {
    this.elementBuilder = elementBuilder;
  }

  @Override
  public B zeebeProperty(final String name, final String value) {
    final ZeebeProperties properties =
        elementBuilder.getCreateSingleExtensionElement(ZeebeProperties.class);
    final ZeebeProperty property = elementBuilder.createChild(properties, ZeebeProperty.class);
    property.setName(name);
    property.setValue(value);

    return elementBuilder;
  }
}
