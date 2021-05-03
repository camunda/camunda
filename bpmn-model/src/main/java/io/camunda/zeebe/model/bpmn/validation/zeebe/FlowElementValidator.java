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

import io.zeebe.model.bpmn.instance.BoundaryEvent;
import io.zeebe.model.bpmn.instance.CallActivity;
import io.zeebe.model.bpmn.instance.DataObject;
import io.zeebe.model.bpmn.instance.DataObjectReference;
import io.zeebe.model.bpmn.instance.DataStoreReference;
import io.zeebe.model.bpmn.instance.EndEvent;
import io.zeebe.model.bpmn.instance.EventBasedGateway;
import io.zeebe.model.bpmn.instance.ExclusiveGateway;
import io.zeebe.model.bpmn.instance.FlowElement;
import io.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.zeebe.model.bpmn.instance.ParallelGateway;
import io.zeebe.model.bpmn.instance.ReceiveTask;
import io.zeebe.model.bpmn.instance.SequenceFlow;
import io.zeebe.model.bpmn.instance.ServiceTask;
import io.zeebe.model.bpmn.instance.StartEvent;
import io.zeebe.model.bpmn.instance.SubProcess;
import io.zeebe.model.bpmn.instance.UserTask;
import java.util.HashSet;
import java.util.Set;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

public class FlowElementValidator implements ModelElementValidator<FlowElement> {

  private static final Set<Class<?>> SUPPORTED_ELEMENT_TYPES = new HashSet<>();

  private static final Set<Class<?>> NON_EXECUTABLE_ELEMENT_TYPES = new HashSet<>();

  static {
    SUPPORTED_ELEMENT_TYPES.add(BoundaryEvent.class);
    SUPPORTED_ELEMENT_TYPES.add(EndEvent.class);
    SUPPORTED_ELEMENT_TYPES.add(EventBasedGateway.class);
    SUPPORTED_ELEMENT_TYPES.add(ExclusiveGateway.class);
    SUPPORTED_ELEMENT_TYPES.add(IntermediateCatchEvent.class);
    SUPPORTED_ELEMENT_TYPES.add(ParallelGateway.class);
    SUPPORTED_ELEMENT_TYPES.add(ReceiveTask.class);
    SUPPORTED_ELEMENT_TYPES.add(SequenceFlow.class);
    SUPPORTED_ELEMENT_TYPES.add(ServiceTask.class);
    SUPPORTED_ELEMENT_TYPES.add(StartEvent.class);
    SUPPORTED_ELEMENT_TYPES.add(SubProcess.class);
    SUPPORTED_ELEMENT_TYPES.add(CallActivity.class);
    SUPPORTED_ELEMENT_TYPES.add(UserTask.class);

    NON_EXECUTABLE_ELEMENT_TYPES.add(DataObject.class);
    NON_EXECUTABLE_ELEMENT_TYPES.add(DataObjectReference.class);
    NON_EXECUTABLE_ELEMENT_TYPES.add(DataStoreReference.class);
  }

  @Override
  public Class<FlowElement> getElementType() {
    return FlowElement.class;
  }

  @Override
  public void validate(
      final FlowElement element, final ValidationResultCollector validationResultCollector) {
    final Class<?> elementType = element.getElementType().getInstanceType();

    if (!SUPPORTED_ELEMENT_TYPES.contains(elementType)
        && !NON_EXECUTABLE_ELEMENT_TYPES.contains(elementType)) {
      validationResultCollector.addError(0, "Elements of this type are not supported");
    }
  }
}
