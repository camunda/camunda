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

public class StateTransition<C extends StateMachineContext>
{
    private final State<C> startState;
    private final String transition;
    private final State<C> endState;

    public StateTransition(State<C> startState, String transition, State<C> endState)
    {
        this.startState = startState;
        this.transition = transition;
        this.endState = endState;
    }

    @Override
    public int hashCode()
    {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((endState == null) ? 0 : endState.hashCode());
        result = prime * result + ((startState == null) ? 0 : startState.hashCode());
        result = prime * result + ((transition == null) ? 0 : transition.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (this == obj)
        {
            return true;
        }
        if (obj == null)
        {
            return false;
        }
        if (getClass() != obj.getClass())
        {
            return false;
        }
        final StateTransition<?> other = (StateTransition<?>) obj;
        if (endState == null)
        {
            if (other.endState != null)
            {
                return false;
            }
        }
        else if (!endState.equals(other.endState))
        {
            return false;
        }
        if (startState == null)
        {
            if (other.startState != null)
            {
                return false;
            }
        }
        else if (!startState.equals(other.startState))
        {
            return false;
        }
        if (transition == null)
        {
            if (other.transition != null)
            {
                return false;
            }
        }
        else if (!transition.equals(other.transition))
        {
            return false;
        }
        return true;
    }

    public State<C> getStartState()
    {
        return startState;
    }

    public String getTransition()
    {
        return transition;
    }

    public State<C> getEndState()
    {
        return endState;
    }



}
