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
package io.camunda.zeebe.model.bpmn.validation;

import static io.camunda.zeebe.model.bpmn.validation.ExpectedValidationResult.expect;
import static java.util.Collections.singletonList;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.instance.EventBasedGateway;
import io.camunda.zeebe.model.bpmn.instance.IntermediateCatchEvent;
import io.camunda.zeebe.model.bpmn.instance.Message;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeSubscription;
import java.util.Arrays;
import org.junit.runners.Parameterized.Parameters;

public class ZeebeEventBasedGatewayValidationTest extends AbstractZeebeValidationTest {

  @Parameters(name = "{index}: {1}")
  public static Object[][] parameters() {
    return new Object[][] {
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .eventBasedGateway()
            .intermediateCatchEvent()
            .timerWithDuration("PT1M")
            .done(),
        singletonList(
            expect(
                EventBasedGateway.class,
                "Event-based gateway must have at least 2 outgoing sequence flows."))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .eventBasedGateway()
            .intermediateCatchEvent()
            .timerWithCycle("R/PT1M")
            .moveToLastGateway()
            .intermediateCatchEvent(
                "catch", c -> c.message(m -> m.name("msg").zeebeCorrelationKeyExpression("foo")))
            .done(),
        singletonList(
            expect(
                IntermediateCatchEvent.class,
                "Intermediate timer catch event must have either a time duration or a time date."))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .eventBasedGateway()
            .intermediateCatchEvent(
                "catch-1", c -> c.message(m -> m.name("msg").zeebeCorrelationKeyExpression("foo")))
            .moveToLastGateway()
            .intermediateCatchEvent(
                "catch-2", c -> c.message(m -> m.name("msg").zeebeCorrelationKeyExpression("foo")))
            .done(),
        singletonList(
            expect(
                EventBasedGateway.class,
                "Multiple message event definitions with the same name 'msg' are not allowed."))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .eventBasedGateway()
            .intermediateCatchEvent(
                "catch-1", c -> c.message(m -> m.name(null).zeebeCorrelationKeyExpression("foo")))
            .moveToLastGateway()
            .intermediateCatchEvent(
                "catch-2", c -> c.message(m -> m.name("msg").zeebeCorrelationKeyExpression(null)))
            .done(),
        Arrays.asList(
            expect(Message.class, "Name must be present and not empty"),
            expect(
                ZeebeSubscription.class,
                "Attribute 'correlationKey' must be present and not empty"))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .eventBasedGateway()
            .receiveTask()
            .message(m -> m.name("this").zeebeCorrelationKeyExpression("foo"))
            .moveToLastGateway()
            .receiveTask()
            .message(m -> m.name("that").zeebeCorrelationKeyExpression("foo"))
            .done(),
        singletonList(
            expect(
                EventBasedGateway.class,
                "Event-based gateway must not have an outgoing sequence flow to other elements than message/timer intermediate catch events."))
      },
      {
        Bpmn.createExecutableProcess("process")
            .startEvent()
            .parallelGateway("parallel")
            .eventBasedGateway("event")
            .intermediateCatchEvent("catch1")
            .timerWithDuration("PT1M")
            .moveToLastGateway()
            .intermediateCatchEvent("catch2")
            .timerWithDuration("PT2M")
            .moveToNode("parallel")
            .connectTo("catch1")
            .done(),
        singletonList(
            expect(
                EventBasedGateway.class,
                "Target elements of an event gateway must not have any additional incoming sequence flows other than that from the event gateway."))
      }
    };
  }
}
