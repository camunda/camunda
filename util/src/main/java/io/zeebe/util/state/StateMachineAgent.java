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
package io.zeebe.util.state;

import java.util.function.Consumer;

import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;

public class StateMachineAgent<C extends StateMachineContext>
{
    public static final int DEFAULT_COMMAND_QUEUE_CAPACITY = 64;

    protected final StateMachine<C> stateMachine;

    protected final ManyToOneConcurrentArrayQueue<StateMachineCommand<C>> commandQueue;
    protected final Consumer<StateMachineCommand<C>> commandConsumer;

    public StateMachineAgent(StateMachine<C> stateMachine)
    {
        this(stateMachine, DEFAULT_COMMAND_QUEUE_CAPACITY);
    }

    public StateMachineAgent(StateMachine<C> stateMachine, int commandQueueCapacity)
    {
        this.stateMachine = stateMachine;

        this.commandQueue = new ManyToOneConcurrentArrayQueue<>(commandQueueCapacity);
        this.commandConsumer = cmd -> cmd.execute(stateMachine.getContext());
    }

    public int doWork()
    {
        int workCount = 0;

        if (stateMachine.getCurrentState().isInterruptable())
        {
            workCount += commandQueue.drain(commandConsumer);
        }

        workCount += stateMachine.doWork();

        return workCount;
    }

    public State<C> getCurrentState()
    {
        return stateMachine.getCurrentState();
    }

    public void addCommand(StateMachineCommand<C> command)
    {
        commandQueue.add(command);
    }

    public void reset()
    {
        stateMachine.reset();

        commandQueue.clear();
    }

}
