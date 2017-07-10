/**
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
package io.zeebe.util.state;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class StateMachineBuilder<C extends StateMachineContext>
{
    private final List<Transition<C>> transitions = new ArrayList<>();

    private final Function<StateMachine<C>, C> contextBuilder;

    private State<C> initialState;

    public StateMachineBuilder(Function<StateMachine<C>, C> contextBuilder)
    {
        this.contextBuilder = contextBuilder;
    }

    public StateMachineBuilder<C> initialState(State<C> initialState)
    {
        this.initialState = initialState;
        return this;
    }

    public TransitionBuilder<C> from(State<C> from)
    {
        return new TransitionBuilder<>(this).from(from);
    }

    public StateMachineBuilder<C> addTransition(Transition<C> transition)
    {
        transitions.add(transition);
        return this;
    }

    public StateMachine<C> build()
    {
        @SuppressWarnings("unchecked")
        final Transition<C>[] transitionArray = transitions.toArray(new Transition[transitions.size()]);

        return new StateMachine<>(contextBuilder, initialState, transitionArray);
    }

}
