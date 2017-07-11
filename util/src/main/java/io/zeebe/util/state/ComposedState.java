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

import java.util.List;

public abstract class ComposedState<C extends StateMachineContext> implements State<C>
{
    public interface Step<C extends StateMachineContext>
    {
        /**
         * Executes the step.
         *
         * @return <code>true</code>, if the step was executed successfully and should continue with the next step.
         */
        boolean doWork(C context);
    }

    public interface FailSafeStep<C extends StateMachineContext> extends Step<C>
    {
        /**
         * Executes the step.
         */
        void work(C context);

        default boolean doWork(C context)
        {
            work(context);
            return true;
        }
    }

    protected abstract List<Step<C>> steps();

    protected Step<C>[] steps;

    protected int nextStep = 0;

    @Override
    public int doWork(C context)
    {
        ensureInitializedSteps();

        int workCount = 0;

        boolean successful = true;
        for (int i = nextStep; i < steps.length && successful; i++)
        {
            nextStep = i;
            successful = steps[i].doWork(context);

            if (successful)
            {
                workCount += 1;
            }
        }

        return workCount;
    }

    @SuppressWarnings("unchecked")
    private void ensureInitializedSteps()
    {
        if (steps == null)
        {
            final List<Step<C>> list = steps();
            steps = list.toArray(new Step[list.size()]);
        }
    }

    @Override
    public void onExit()
    {
        nextStep = 0;
    }

}
