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
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeHeader;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutputBehavior;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskDefinition;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeTaskHeaders;

/** @author Sebastian Menski */
public abstract class AbstractServiceTaskBuilder<B extends AbstractServiceTaskBuilder<B>>
    extends AbstractTaskBuilder<B, ServiceTask> {

  protected AbstractServiceTaskBuilder(
      BpmnModelInstance modelInstance, ServiceTask element, Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the implementation of the build service task.
   *
   * @param implementation the implementation to set
   * @return the builder object
   */
  public B implementation(String implementation) {
    element.setImplementation(implementation);
    return myself;
  }

  public B zeebeTaskType(String type) {
    final ZeebeTaskDefinition taskDefinition =
        getCreateSingleExtensionElement(ZeebeTaskDefinition.class);
    taskDefinition.setType(type);
    return myself;
  }

  public B zeebeTaskRetries(int retries) {
    final ZeebeTaskDefinition taskDefinition =
        getCreateSingleExtensionElement(ZeebeTaskDefinition.class);
    taskDefinition.setRetries(retries);
    return myself;
  }

  public B zeebeTaskHeader(String key, String value) {
    final ZeebeTaskHeaders taskHeaders = getCreateSingleExtensionElement(ZeebeTaskHeaders.class);
    final ZeebeHeader header = createChild(taskHeaders, ZeebeHeader.class);
    header.setKey(key);
    header.setValue(value);

    return myself;
  }

  public B zeebeOutputBehavior(ZeebeOutputBehavior outputBehavior) {
    final ZeebeIoMapping ioMapping = getCreateSingleExtensionElement(ZeebeIoMapping.class);
    ioMapping.setOutputBehavhior(outputBehavior);

    return myself;
  }

  public B zeebeInput(String source, String target) {
    final ZeebeIoMapping ioMapping = getCreateSingleExtensionElement(ZeebeIoMapping.class);
    final ZeebeInput input = createChild(ioMapping, ZeebeInput.class);
    input.setSource(source);
    input.setTarget(target);

    return myself;
  }

  public B zeebeOutput(String source, String target) {
    final ZeebeIoMapping ioMapping = getCreateSingleExtensionElement(ZeebeIoMapping.class);
    final ZeebeOutput input = createChild(ioMapping, ZeebeOutput.class);
    input.setSource(source);
    input.setTarget(target);

    return myself;
  }
}
