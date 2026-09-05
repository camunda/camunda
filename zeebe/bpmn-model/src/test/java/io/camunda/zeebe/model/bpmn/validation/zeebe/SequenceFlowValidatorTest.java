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
package io.camunda.zeebe.model.bpmn.validation.zeebe;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.SequenceFlow;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;
import org.junit.jupiter.api.Test;

final class SequenceFlowValidatorTest {

  private final SequenceFlowValidator validator = new SequenceFlowValidator();

  @Test
  void shouldReportErrorWhenTargetRefIsUnresolved() {
    final SequenceFlow sequenceFlow = mock(SequenceFlow.class);
    when(sequenceFlow.getId()).thenReturn("flow1");
    when(sequenceFlow.getSource()).thenReturn(mock(FlowNode.class));
    when(sequenceFlow.getTarget()).thenReturn(null);

    final ValidationResultCollector collector = mock(ValidationResultCollector.class);

    validator.validate(sequenceFlow, collector);

    verify(collector).addError(0, "Attribute 'targetRef' must reference a valid flow node");
    verifyNoMoreInteractions(collector);
  }

  @Test
  void shouldReportErrorWhenSourceRefIsUnresolved() {
    final SequenceFlow sequenceFlow = mock(SequenceFlow.class);
    when(sequenceFlow.getId()).thenReturn("flow1");
    when(sequenceFlow.getSource()).thenReturn(null);
    when(sequenceFlow.getTarget()).thenReturn(mock(FlowNode.class));

    final ValidationResultCollector collector = mock(ValidationResultCollector.class);

    validator.validate(sequenceFlow, collector);

    verify(collector).addError(0, "Attribute 'sourceRef' must reference a valid flow node");
    verifyNoMoreInteractions(collector);
  }

  @Test
  void shouldReportErrorsWhenBothRefsAreUnresolved() {
    final SequenceFlow sequenceFlow = mock(SequenceFlow.class);
    when(sequenceFlow.getId()).thenReturn("flow1");
    when(sequenceFlow.getSource()).thenReturn(null);
    when(sequenceFlow.getTarget()).thenReturn(null);

    final ValidationResultCollector collector = mock(ValidationResultCollector.class);

    validator.validate(sequenceFlow, collector);

    verify(collector).addError(0, "Attribute 'sourceRef' must reference a valid flow node");
    verify(collector).addError(0, "Attribute 'targetRef' must reference a valid flow node");
    verifyNoMoreInteractions(collector);
  }
}
