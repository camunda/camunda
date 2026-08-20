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

import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.ServiceTask;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAgentDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeAgentType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeBindingType;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLinkedResource;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeLinkedResources;
import java.util.function.Consumer;

/**
 * @author Sebastian Menski
 */
public abstract class AbstractServiceTaskBuilder<B extends AbstractServiceTaskBuilder<B>>
    extends AbstractJobWorkerTaskBuilder<B, ServiceTask> {

  protected AbstractServiceTaskBuilder(
      final BpmnModelInstance modelInstance, final ServiceTask element, final Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the implementation of the build service task.
   *
   * @param implementation the implementation to set
   * @return the builder object
   */
  public B implementation(final String implementation) {
    element.setImplementation(implementation);
    return myself;
  }

  /**
   * Sets the zeebe:modelerTemplate attribute of the build service task.
   *
   * @param modelerTemplate the element template id to set
   * @return the builder object
   */
  public B zeebeModelerTemplate(final String modelerTemplate) {
    element.setModelerTemplate(modelerTemplate);
    return myself;
  }

  public B zeebeLinkedResources(
      final Consumer<LinkedResourceBuilder> linkedResourceBuilderConsumer) {
    final ZeebeLinkedResource linkedResource = createLinkedResourceElement();
    linkedResource.setBindingType(ZeebeBindingType.latest);

    final LinkedResourceBuilder builder = new LinkedResourceBuilder(linkedResource, myself);
    linkedResourceBuilderConsumer.accept(builder);
    return myself;
  }

  /**
   * Marks this service task as an agent definition.
   *
   * @param agentType the agent type declared on the marker
   * @return the builder object
   */
  public B zeebeAgentDefinition(final ZeebeAgentType agentType) {
    final ZeebeAgentDefinition agentDefinition =
        myself.getCreateSingleExtensionElement(ZeebeAgentDefinition.class);
    agentDefinition.setAgentType(agentType);
    return myself;
  }

  /**
   * Marks this service task as a Camunda-native AI agent task.
   *
   * @return the builder object
   */
  public B zeebeAiAgentTaskDefinition() {
    return zeebeAgentDefinition(ZeebeAgentType.aiAgentTask);
  }

  /**
   * Marks this service task as an external agent.
   *
   * @return the builder object
   */
  public B zeebeExternalAgentDefinition() {
    return zeebeAgentDefinition(ZeebeAgentType.external);
  }

  private ZeebeLinkedResource createLinkedResourceElement() {
    final ZeebeLinkedResources linkedResources =
        myself.getCreateSingleExtensionElement(ZeebeLinkedResources.class);
    return myself.createChild(linkedResources, ZeebeLinkedResource.class);
  }
}
