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
package io.zeebe.util.actor;

/**
 * Schedule the given agents and invoke them within a duty cycle.
 *
 * <p>
 * Use {@link ActorReference#close()} to remove a scheduled actor from the duty
 * cycle.
 */
public interface ActorScheduler extends AutoCloseable
{
    /**
     * Schedule the given actor.
     */
    ActorReference schedule(Actor actor);

    @Override
    void close();

}