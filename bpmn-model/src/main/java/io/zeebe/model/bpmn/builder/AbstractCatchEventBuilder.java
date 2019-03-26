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
import io.zeebe.model.bpmn.builder.zeebe.MessageBuilder;
import io.zeebe.model.bpmn.instance.CatchEvent;
import io.zeebe.model.bpmn.instance.CompensateEventDefinition;
import io.zeebe.model.bpmn.instance.ConditionalEventDefinition;
import io.zeebe.model.bpmn.instance.Message;
import io.zeebe.model.bpmn.instance.MessageEventDefinition;
import io.zeebe.model.bpmn.instance.SignalEventDefinition;
import io.zeebe.model.bpmn.instance.TimeCycle;
import io.zeebe.model.bpmn.instance.TimeDate;
import io.zeebe.model.bpmn.instance.TimeDuration;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeInput;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeIoMapping;
import io.zeebe.model.bpmn.instance.zeebe.ZeebeOutput;
import java.util.function.Consumer;

/** @author Sebastian Menski */
public abstract class AbstractCatchEventBuilder<
        B extends AbstractCatchEventBuilder<B, E>, E extends CatchEvent>
    extends AbstractEventBuilder<B, E> implements ZeebeVariablesMappingBuilder<B> {

  protected AbstractCatchEventBuilder(
      BpmnModelInstance modelInstance, E element, Class<?> selfType) {
    super(modelInstance, element, selfType);
  }

  /**
   * Sets the event to be parallel multiple
   *
   * @return the builder object
   */
  public B parallelMultiple() {
    element.isParallelMultiple();
    return myself;
  }

  /**
   * Sets an event definition for the given message name. If already a message with this name exists
   * it will be used, otherwise a new message is created.
   *
   * @param messageName the name of the message
   * @return the builder object
   */
  public B message(String messageName) {
    final MessageEventDefinition messageEventDefinition = createMessageEventDefinition(messageName);
    element.getEventDefinitions().add(messageEventDefinition);

    return myself;
  }

  public B message(Consumer<MessageBuilder> messageBuilderConsumer) {
    final MessageEventDefinition messageEventDefinition =
        createInstance(MessageEventDefinition.class);
    element.getEventDefinitions().add(messageEventDefinition);

    final Message message = createMessage();
    final MessageBuilder builder = new MessageBuilder(modelInstance, message);

    messageBuilderConsumer.accept(builder);

    messageEventDefinition.setMessage(message);

    return myself;
  }

  public MessageEventDefinitionBuilder messageEventDefinition() {
    final MessageEventDefinition eventDefinition = createEmptyMessageEventDefinition();
    element.getEventDefinitions().add(eventDefinition);
    return new MessageEventDefinitionBuilder(modelInstance, eventDefinition);
  }

  /**
   * Sets an event definition for the given signal name. If already a signal with this name exists
   * it will be used, otherwise a new signal is created.
   *
   * @param signalName the name of the signal
   * @return the builder object
   */
  public B signal(String signalName) {
    final SignalEventDefinition signalEventDefinition = createSignalEventDefinition(signalName);
    element.getEventDefinitions().add(signalEventDefinition);

    return myself;
  }

  /**
   * Sets an event definition for the timer with a time date.
   *
   * @param timerDate the time date of the timer
   * @return the builder object
   */
  public B timerWithDate(String timerDate) {
    final TimeDate timeDate = createInstance(TimeDate.class);
    timeDate.setTextContent(timerDate);

    final TimerEventDefinition timerEventDefinition = createInstance(TimerEventDefinition.class);
    timerEventDefinition.setTimeDate(timeDate);

    element.getEventDefinitions().add(timerEventDefinition);

    return myself;
  }

  /**
   * Sets an event definition for the timer with a time duration.
   *
   * @param timerDuration the time duration of the timer
   * @return the builder object
   */
  public B timerWithDuration(String timerDuration) {
    final TimeDuration timeDuration = createInstance(TimeDuration.class);
    timeDuration.setTextContent(timerDuration);

    final TimerEventDefinition timerEventDefinition = createInstance(TimerEventDefinition.class);
    timerEventDefinition.setTimeDuration(timeDuration);

    element.getEventDefinitions().add(timerEventDefinition);

    return myself;
  }

  /**
   * Sets an event definition for the timer with a time cycle.
   *
   * @param timerCycle the time cycle of the timer
   * @return the builder object
   */
  public B timerWithCycle(String timerCycle) {
    final TimeCycle timeCycle = createInstance(TimeCycle.class);
    timeCycle.setTextContent(timerCycle);

    final TimerEventDefinition timerEventDefinition = createInstance(TimerEventDefinition.class);
    timerEventDefinition.setTimeCycle(timeCycle);

    element.getEventDefinitions().add(timerEventDefinition);

    return myself;
  }

  public CompensateEventDefinitionBuilder compensateEventDefinition() {
    return compensateEventDefinition(null);
  }

  public CompensateEventDefinitionBuilder compensateEventDefinition(String id) {
    final CompensateEventDefinition eventDefinition =
        createInstance(CompensateEventDefinition.class);
    if (id != null) {
      eventDefinition.setId(id);
    }

    element.getEventDefinitions().add(eventDefinition);
    return new CompensateEventDefinitionBuilder(modelInstance, eventDefinition);
  }

  public ConditionalEventDefinitionBuilder conditionalEventDefinition() {
    return conditionalEventDefinition(null);
  }

  public ConditionalEventDefinitionBuilder conditionalEventDefinition(String id) {
    final ConditionalEventDefinition eventDefinition =
        createInstance(ConditionalEventDefinition.class);
    if (id != null) {
      eventDefinition.setId(id);
    }

    element.getEventDefinitions().add(eventDefinition);
    return new ConditionalEventDefinitionBuilder(modelInstance, eventDefinition);
  }

  @Override
  public B condition(String condition) {
    conditionalEventDefinition().condition(condition);
    return myself;
  }

  @Override
  public B zeebeInput(String source, String target) {
    final ZeebeIoMapping ioMapping = getCreateSingleExtensionElement(ZeebeIoMapping.class);
    final ZeebeInput input = createChild(ioMapping, ZeebeInput.class);
    input.setSource(source);
    input.setTarget(target);

    return myself;
  }

  @Override
  public B zeebeOutput(String source, String target) {
    final ZeebeIoMapping ioMapping = getCreateSingleExtensionElement(ZeebeIoMapping.class);
    final ZeebeOutput input = createChild(ioMapping, ZeebeOutput.class);
    input.setSource(source);
    input.setTarget(target);

    return myself;
  }
}
