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

public interface State<C extends StateMachineContext>
{
    /**
     * Executes the work (i.e. behavior). Returns the workload which is either a
     * positive number or 0 to indicate no work was currently available.
     *
     * @return the workload
     */
    int doWork(C context) throws Exception;

    default boolean isInterruptable()
    {
        return true;
    }

    default void onFailure(C context, Exception e)
    {
        throw new RuntimeException(e);
    }

    default void onExit()
    {
        // do nothing
    }

    /**
     * <p>It is not guaranteed that {@link #doWork(StateMachineContext)} is invoked after that (e.g. consider
     * another command that changes the state yet again before doWork is called).
     *
     * <p>You can change that if you need this behavior.
     */
    default void onEnter()
    {
    }

    default boolean isWaitState()
    {
        return false;
    }

}
