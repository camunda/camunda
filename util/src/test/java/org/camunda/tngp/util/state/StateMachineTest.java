/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.camunda.tngp.util.state;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class StateMachineTest
{
    private static final int TRANSITION_NEXT = 1;
    private static final int TRANSITION_PREVIOUS = 2;

    private final WaitState<SimpleStateMachineContext> a = context ->
    {
    };

    private final WaitState<SimpleStateMachineContext> b = context ->
    {
    };

    private final WaitState<SimpleStateMachineContext> c = context -> context.take(TRANSITION_NEXT);
    private final WaitState<SimpleStateMachineContext> d = context -> context.take(TRANSITION_PREVIOUS);

    private final WaitState<CustomStateMachineContext> e = context ->
    {
        context.setData("bar");
        context.take(TRANSITION_NEXT);
    };

    private final WaitState<CustomStateMachineContext> f = context ->
    {
        if ("bar".equals(context.getData()))
        {
            context.take(TRANSITION_PREVIOUS);
        }
    };

    private final State<SimpleStateMachineContext> g = context ->
    {
        return 2;
    };

    private class CustomStateMachineContext extends SimpleStateMachineContext
    {
        private String data = "";

        CustomStateMachineContext(StateMachine<?> stateMachine)
        {
            super(stateMachine);
        }

        public String getData()
        {
            return data;
        }

        public void setData(String data)
        {
            this.data = data;
        }

    }

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void shouldTakeTransition()
    {
        final StateMachine<SimpleStateMachineContext> stateMachine = StateMachine.builder()
            .initialState(a)
            .from(a).take(TRANSITION_NEXT).to(b)
            .from(b).take(TRANSITION_PREVIOUS).to(a)
            .build();

        assertThat(stateMachine.getCurrentState()).isEqualTo(a);

        stateMachine.take(TRANSITION_NEXT);
        assertThat(stateMachine.getCurrentState()).isEqualTo(b);

        stateMachine.take(TRANSITION_PREVIOUS);
        assertThat(stateMachine.getCurrentState()).isEqualTo(a);
    }

    @Test
    public void shouldTakeTransitionInsideTheState()
    {
        final StateMachine<SimpleStateMachineContext> stateMachine = StateMachine.builder()
            .initialState(c)
            .from(c).take(TRANSITION_NEXT).to(d)
            .from(d).take(TRANSITION_PREVIOUS).to(c)
            .build();

        assertThat(stateMachine.getCurrentState()).isEqualTo(c);

        stateMachine.doWork();
        assertThat(stateMachine.getCurrentState()).isEqualTo(d);

        stateMachine.doWork();
        assertThat(stateMachine.getCurrentState()).isEqualTo(c);
    }

    @Test
    public void shouldShareDataBetweenStates()
    {
        final StateMachine<CustomStateMachineContext> stateMachine = StateMachine. <CustomStateMachineContext> builder(s -> new CustomStateMachineContext(s))
            .initialState(e)
            .from(e).take(TRANSITION_NEXT).to(f)
            .from(f).take(TRANSITION_PREVIOUS).to(e)
            .build();

        assertThat(stateMachine.getCurrentState()).isEqualTo(e);

        stateMachine.getContext().setData("foo");

        stateMachine.doWork();
        assertThat(stateMachine.getCurrentState()).isEqualTo(f);

        stateMachine.doWork();
        assertThat(stateMachine.getCurrentState()).isEqualTo(e);
    }

    @Test
    public void shouldDoTheCurrentStateWork()
    {
        final StateMachine<SimpleStateMachineContext> stateMachine = StateMachine.builder()
                .initialState(g)
                .build();

        final int result = stateMachine.doWork();

        assertThat(result).isEqualTo(2);
    }

    @Test
    public void shouldNotTakeNonExistingTransition()
    {
        final StateMachine<SimpleStateMachineContext> stateMachine = StateMachine.builder()
                .initialState(a)
                .from(a).take(TRANSITION_NEXT).to(b)
                .from(b).take(TRANSITION_PREVIOUS).to(a)
                .build();

        assertThat(stateMachine.getCurrentState()).isEqualTo(a);

        final boolean hasTaken = stateMachine.tryTake(TRANSITION_PREVIOUS);

        assertThat(hasTaken).isFalse();
    }

    @Test
    public void shouldFailToTakeNonExistingTransition()
    {
        final StateMachine<SimpleStateMachineContext> stateMachine = StateMachine.builder()
                .initialState(a)
                .from(a).take(TRANSITION_NEXT).to(b)
                .from(b).take(TRANSITION_PREVIOUS).to(a)
                .build();

        assertThat(stateMachine.getCurrentState()).isEqualTo(a);

        thrown.expect(NoSuchTransitionException.class);

        stateMachine.take(TRANSITION_PREVIOUS);
    }

}
