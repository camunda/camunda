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

public class Transition<C extends StateMachineContext>
{
    private final State<C> current;
    private final int transitionId;
    private final State<C> next;

    public Transition(State<C> current, int transitionId, State<C> next)
    {
        this.current = current;
        this.transitionId = transitionId;
        this.next = next;
    }

    public boolean match(State<C> from, int transitionId)
    {
        return this.transitionId == transitionId && this.current.equals(from);
    }

    public State<C> getNextState()
    {
        return next;
    }

}