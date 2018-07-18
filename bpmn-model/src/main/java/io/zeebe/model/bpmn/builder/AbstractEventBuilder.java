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

package io.zeebe.model.bpmn.builder;

import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.model.bpmn.instance.Event;
import io.zeebe.model.bpmn.instance.camunda.CamundaInputOutput;
import io.zeebe.model.bpmn.instance.camunda.CamundaInputParameter;
import io.zeebe.model.bpmn.instance.camunda.CamundaOutputParameter;

/** @author Sebastian Menski */
public abstract class AbstractEventBuilder<B extends AbstractEventBuilder<B, E>, E extends Event>
    extends AbstractFlowNodeBuilder<B, E> {

  protected AbstractEventBuilder(BpmnModelInstance modelInstance, E element, Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Creates a new camunda input parameter extension element with the given name and value.
   *
   * @param name the name of the input parameter
   * @param value the value of the input parameter
   * @return the builder object
   */
  public B camundaInputParameter(String name, String value) {
    final CamundaInputOutput camundaInputOutput =
        getCreateSingleExtensionElement(CamundaInputOutput.class);

    final CamundaInputParameter camundaInputParameter =
        createChild(camundaInputOutput, CamundaInputParameter.class);
    camundaInputParameter.setCamundaName(name);
    camundaInputParameter.setTextContent(value);

    return myself;
  }

  /**
   * Creates a new camunda output parameter extension element with the given name and value.
   *
   * @param name the name of the output parameter
   * @param value the value of the output parameter
   * @return the builder object
   */
  public B camundaOutputParameter(String name, String value) {
    final CamundaInputOutput camundaInputOutput =
        getCreateSingleExtensionElement(CamundaInputOutput.class);

    final CamundaOutputParameter camundaOutputParameter =
        createChild(camundaInputOutput, CamundaOutputParameter.class);
    camundaOutputParameter.setCamundaName(name);
    camundaOutputParameter.setTextContent(value);

    return myself;
  }
}
