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

/** A fluent builder for elements with variable mappings. */
public interface ZeebeVariablesMappingBuilder<B> {

  B zeebeInputExpression(String sourceExpression, String target);

  B zeebeOutputExpression(String sourceExpression, String target);

  B zeebeInput(String source, String target);

  default B zeebeInput(final String target) {
    return zeebeInput(null, target);
  }

  B zeebeOutput(String source, String target);
}
