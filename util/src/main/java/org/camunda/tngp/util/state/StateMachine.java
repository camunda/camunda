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

import java.util.function.Function;

public class StateMachine<C extends StateMachineContext>
{
    private final Transition<C>[] transitions;

    private final State<C> intialState;
    private final Function<StateMachine<C>, C> contextBuilder;

    private State<C> currentState;
    private C context;


    public StateMachine(Function<StateMachine<C>, C> contextBuilder, State<C> initialState, Transition<C>[] transitions)
    {
        this.transitions = transitions;

        this.intialState = initialState;
        this.currentState = initialState;

        this.contextBuilder = contextBuilder;
        this.context = contextBuilder.apply(this);
    }

    /**
     * Takes the transition with the given id.
     *
     * @param transitionId
     *            id of the transition
     *
     * @throws NoSuchTransitionException
     *             if the current state doesn't have a transition with the given
     *             id
     */
    public void take(int transitionId)
    {
        final boolean hasTaken = tryTake(transitionId);

        if (!hasTaken)
        {
            throw new NoSuchTransitionException();
        }
    }

    /**
     * Takes the transition with the given id if exists.
     *
     * @param transitionId
     *            id of the transition
     *
     * @return <code>true</code> if a transitions is taken, otherwise
     *         <code>false</code>
     *
     */
    public boolean tryTake(int transitionId)
    {
        boolean hasTaken = false;
        final Transition<C> transition = getTransition(transitionId);

        if (transition != null)
        {
            currentState = transition.getNextState();
            hasTaken = true;
        }
        return hasTaken;
    }

    private Transition<C> getTransition(int transitionId)
    {
        for (int i = 0; i < transitions.length; i++)
        {
            final Transition<C> transition = transitions[i];
            if (transition.match(currentState, transitionId))
            {
                return transition;
            }
        }
        return null;
    }

    /**
     * Executes the current state's work (i.e. behavior). Returns the workload
     * which is either a positive number or 0 to indicate no work was currently
     * available.
     *
     * @return the workload of the current state
     */
    public int doWork()
    {
        return currentState.doWork(context);
    }

    /**
     * Returns the current state.
     */
    public State<C> getCurrentState()
    {
        return currentState;
    }

    /**
     * Returns the context object.
     */
    public C getContext()
    {
        return context;
    }

    /**
     * Restores the initial state.
     */
    public void reset()
    {
        currentState = intialState;
        context = contextBuilder.apply(this);
    }

    public static StateMachineBuilder<SimpleStateMachineContext> builder()
    {
        return new StateMachineBuilder<>(s -> new SimpleStateMachineContext(s));
    }

    public static <C extends StateMachineContext> StateMachineBuilder<C> builder(Function<StateMachine<C>, C> contextBuilder)
    {
        return new StateMachineBuilder<>(contextBuilder);
    }

}
