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
package io.camunda.zeebe.model.bpmn.instance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;

public class TimerEventDefinitionTest extends AbstractEventDefinitionTest {

  @Override
  public Collection<ChildElementAssumption> getChildElementAssumptions() {
    return Arrays.asList(
        new ChildElementAssumption(TimeDate.class, 0, 1),
        new ChildElementAssumption(TimeDuration.class, 0, 1),
        new ChildElementAssumption(TimeCycle.class, 0, 1));
  }

  @Test
  public void getElementDefinition() {
    final List<TimerEventDefinition> eventDefinitions =
        eventDefinitionQuery.filterByType(TimerEventDefinition.class).list();
    assertThat(eventDefinitions).hasSize(3);
    for (final TimerEventDefinition eventDefinition : eventDefinitions) {
      final String id = eventDefinition.getId();
      String textContent = null;
      if ("date".equals(id)) {
        textContent = eventDefinition.getTimeDate().getTextContent();
      } else if ("duration".equals(id)) {
        textContent = eventDefinition.getTimeDuration().getTextContent();
      } else if ("cycle".equals(id)) {
        textContent = eventDefinition.getTimeCycle().getTextContent();
      }

      assertThat(textContent).isEqualTo("${test}");
    }
  }
}
