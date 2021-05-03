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

package io.zeebe.model.bpmn.impl.instance;

import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN20_NS;
import static io.zeebe.model.bpmn.impl.BpmnModelConstants.BPMN_ELEMENT_TIMER_EVENT_DEFINITION;

import io.zeebe.model.bpmn.instance.EventDefinition;
import io.zeebe.model.bpmn.instance.TimeCycle;
import io.zeebe.model.bpmn.instance.TimeDate;
import io.zeebe.model.bpmn.instance.TimeDuration;
import io.zeebe.model.bpmn.instance.TimerEventDefinition;
import org.camunda.bpm.model.xml.ModelBuilder;
import org.camunda.bpm.model.xml.impl.instance.ModelTypeInstanceContext;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder;
import org.camunda.bpm.model.xml.type.ModelElementTypeBuilder.ModelTypeInstanceProvider;
import org.camunda.bpm.model.xml.type.child.ChildElement;
import org.camunda.bpm.model.xml.type.child.SequenceBuilder;

/**
 * The BPMN timerEventDefinition element
 *
 * @author Sebastian Menski
 */
public class TimerEventDefinitionImpl extends EventDefinitionImpl implements TimerEventDefinition {

  protected static ChildElement<TimeDate> timeDateChild;
  protected static ChildElement<TimeDuration> timeDurationChild;
  protected static ChildElement<TimeCycle> timeCycleChild;

  public TimerEventDefinitionImpl(final ModelTypeInstanceContext context) {
    super(context);
  }

  public static void registerType(final ModelBuilder modelBuilder) {
    final ModelElementTypeBuilder typeBuilder =
        modelBuilder
            .defineType(TimerEventDefinition.class, BPMN_ELEMENT_TIMER_EVENT_DEFINITION)
            .namespaceUri(BPMN20_NS)
            .extendsType(EventDefinition.class)
            .instanceProvider(
                new ModelTypeInstanceProvider<TimerEventDefinition>() {
                  @Override
                  public TimerEventDefinition newInstance(
                      final ModelTypeInstanceContext instanceContext) {
                    return new TimerEventDefinitionImpl(instanceContext);
                  }
                });

    final SequenceBuilder sequenceBuilder = typeBuilder.sequence();

    timeDateChild = sequenceBuilder.element(TimeDate.class).build();

    timeDurationChild = sequenceBuilder.element(TimeDuration.class).build();

    timeCycleChild = sequenceBuilder.element(TimeCycle.class).build();

    typeBuilder.build();
  }

  @Override
  public TimeDate getTimeDate() {
    return timeDateChild.getChild(this);
  }

  @Override
  public void setTimeDate(final TimeDate timeDate) {
    timeDateChild.setChild(this, timeDate);
  }

  @Override
  public TimeDuration getTimeDuration() {
    return timeDurationChild.getChild(this);
  }

  @Override
  public void setTimeDuration(final TimeDuration timeDuration) {
    timeDurationChild.setChild(this, timeDuration);
  }

  @Override
  public TimeCycle getTimeCycle() {
    return timeCycleChild.getChild(this);
  }

  @Override
  public void setTimeCycle(final TimeCycle timeCycle) {
    timeCycleChild.setChild(this, timeCycle);
  }
}
