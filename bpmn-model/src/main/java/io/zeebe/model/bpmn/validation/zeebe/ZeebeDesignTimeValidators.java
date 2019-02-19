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

import java.util.ArrayList;
import java.util.Collection;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;

public class ZeebeDesignTimeValidators {

  public static final Collection<ModelElementValidator<?>> VALIDATORS;

  static {
    VALIDATORS = new ArrayList<>();
    VALIDATORS.add(new ActivityValidator());
    VALIDATORS.add(new BoundaryEventValidator());
    VALIDATORS.add(new DefinitionsValidator());
    VALIDATORS.add(new EndEventValidator());
    VALIDATORS.add(new EventDefinitionValidator());
    VALIDATORS.add(new EventBasedGatewayValidator());
    VALIDATORS.add(new ExclusiveGatewayValidator());
    VALIDATORS.add(new FlowElementValidator());
    VALIDATORS.add(new FlowNodeValidator());
    VALIDATORS.add(new IntermediateCatchEventValidator());
    VALIDATORS.add(new MessageEventDefinitionValidator());
    VALIDATORS.add(new MessageValidator());
    VALIDATORS.add(new ProcessValidator());
    VALIDATORS.add(new SequenceFlowValidator());
    VALIDATORS.add(new ServiceTaskValidator());
    VALIDATORS.add(new ReceiveTaskValidator());
    VALIDATORS.add(new StartEventValidator());
    VALIDATORS.add(new SubProcessValidator());
    VALIDATORS.add(new TimerEventDefinitionValidator());
    VALIDATORS.add(new ZeebeTaskDefinitionValidator());
    VALIDATORS.add(new ZeebeSubscriptionValidator());
  }
}
