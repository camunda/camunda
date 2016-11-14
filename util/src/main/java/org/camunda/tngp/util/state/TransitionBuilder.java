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

import static org.camunda.tngp.util.EnsureUtil.ensureNotNull;

public class TransitionBuilder<C extends StateMachineContext>
{
    private final StateMachineBuilder<C> builder;

    private State<C> from;
    private int transitionId;
    private State<C> to;

    public TransitionBuilder(StateMachineBuilder<C> builder)
    {
        this.builder = builder;
    }

    public TransitionBuilder<C> from(State<C> from)
    {
        this.from = from;
        return this;
    }

    public TransitionBuilder<C> take(int transitionId)
    {
        this.transitionId = transitionId;
        return this;
    }

    public StateMachineBuilder<C> to(State<C> to)
    {
        this.to = to;

        final Transition<C> transition = build();
        builder.addTransition(transition);

        return builder;
    }

    private Transition<C> build()
    {
        ensureNotNull("from", from);
        ensureNotNull("transition id", transitionId);
        ensureNotNull("to", to);

        return new Transition<>(from, transitionId, to);
    }

}
