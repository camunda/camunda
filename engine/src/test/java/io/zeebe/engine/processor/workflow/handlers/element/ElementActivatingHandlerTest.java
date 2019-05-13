/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow.handlers.element;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.handlers.IOMappingHelper;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.msgpack.mapping.MappingException;
import io.zeebe.protocol.ErrorType;
import io.zeebe.protocol.impl.record.value.incident.IncidentRecord;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.util.buffer.BufferUtil;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class ElementActivatingHandlerTest extends ElementHandlerTestCase<ExecutableFlowNode> {
  @Mock public IOMappingHelper ioMappingHelper;

  private final WorkflowInstanceIntent nextState = WorkflowInstanceIntent.ELEMENT_ACTIVATED;
  private ElementActivatingHandler<ExecutableFlowNode> handler;

  @Override
  @Before
  public void setUp() {
    super.setUp();
    handler = new ElementActivatingHandler<>(nextState, ioMappingHelper);
  }

  @Test
  public void shouldNotHandleStateIfNoElementGiven() {
    // given

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isFalse();
  }

  @Test
  public void shouldNotHandleStateIfInMemoryStateIsDifferent() {
    // given
    final ElementInstance instance =
        createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_ACTIVATING);
    instance.setState(WorkflowInstanceIntent.ELEMENT_TERMINATED);
    elementInstanceState.updateInstance(instance);

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isFalse();
  }

  @Test
  public void shouldNotHandleStateIfFlowScopeIsNotActivated() {
    // given
    final ElementInstance flowScope =
        newElementInstance(WorkflowInstanceIntent.ELEMENT_TERMINATING);
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_ACTIVATING, flowScope);

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isFalse();
  }

  @Test
  public void shouldHandleStateIfElementIsRootFlowScope() {
    // given
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isTrue();
  }

  @Test
  public void shouldApplyInputMappings() {
    // given
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // when
    handler.handle(context);

    // then
    verify(ioMappingHelper, times(1)).applyInputMappings(context);
  }

  @Test
  public void shouldRaiseIncidentOnMappingException() {
    // given
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_ACTIVATING);

    // when
    doThrow(new MappingException("fail")).when(ioMappingHelper).applyInputMappings(context);
    final boolean handled = handler.handleState(context);

    // then
    final IncidentRecord raisedIncident = getRaisedIncident();
    assertThat(handled).isFalse();
    assertThat(raisedIncident)
        .extracting(IncidentRecord::getErrorType, IncidentRecord::getErrorMessage)
        .containsExactly(ErrorType.IO_MAPPING_ERROR, BufferUtil.wrapString("fail"));
  }
}
