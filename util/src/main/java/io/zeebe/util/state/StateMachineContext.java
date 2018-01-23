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
package io.zeebe.util.state;

/**
 * Context object of a state machine which can be used to take transitions and
 * share data between states.
 */
public interface StateMachineContext
{
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
    void take(int transitionId);

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
    boolean tryTake(int transitionId);

    /**
     * Set the context in the initial state.
     */
    default void reset()
    {
        // do nothing by default
    }

    default String describeStateMachine()
    {
        return String.valueOf(System.identityHashCode(this));
    }

}
