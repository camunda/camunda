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

import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.EnumSet;
import java.util.Set;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class EventOccurredHandlerTest extends ElementHandlerTestCase<ExecutableFlowNode> {
  private EventOccurredHandler<ExecutableFlowNode> handler;

  @Override
  @Before
  public void setUp() {
    super.setUp();
    handler = new EventOccurredHandler<>();
  }

  @Test
  public void shouldNotHandleStateIfNoElementGiven() {
    // given
    final ElementInstance instance =
        createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_ACTIVATED);
    instance.getValue().setWorkflowInstanceKey(zeebeStateRule.getKeyGenerator().nextKey());
    zeebeStateRule
        .getZeebeState()
        .getWorkflowState()
        .getElementInstanceState()
        .removeInstance(instance.getKey());

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isFalse();
  }

  @Test
  public void shouldNotHandleStateIfElementIsNotActive() {
    // given
    final Set<WorkflowInstanceIntent> inactiveStates =
        EnumSet.complementOf(EnumSet.of(WorkflowInstanceIntent.ELEMENT_ACTIVATED));

    // when - then
    for (final WorkflowInstanceIntent inactiveState : inactiveStates) {
      final ElementInstance instance = createAndSetContextElementInstance(inactiveState);
      instance.getValue().setWorkflowInstanceKey(zeebeStateRule.getKeyGenerator().nextKey());

      Assertions.assertThat(handler.shouldHandleState(context)).isFalse();
    }
  }

  @Test
  public void shouldHandleStateIfElementHasNoWorkflowInstance() {
    // given
    final Set<WorkflowInstanceIntent> inactiveStates =
        EnumSet.complementOf(EnumSet.of(WorkflowInstanceIntent.ELEMENT_ACTIVATED));

    // when - then
    for (final WorkflowInstanceIntent inactiveState : inactiveStates) {
      createAndSetContextElementInstance(inactiveState);

      Assertions.assertThat(handler.shouldHandleState(context)).isTrue();
    }
  }

  @Test
  public void shouldHandleStateIfElementIsActive() {
    // given
    createAndSetContextElementInstance(WorkflowInstanceIntent.ELEMENT_ACTIVATED);

    // when - then
    Assertions.assertThat(handler.shouldHandleState(context)).isTrue();
  }

  @Override
  protected ElementInstance createAndSetContextElementInstance(WorkflowInstanceIntent state) {
    final ElementInstance instance = super.createAndSetContextElementInstance(state);
    context.getRecord().getMetadata().intent(WorkflowInstanceIntent.EVENT_OCCURRED);

    return instance;
  }
}
