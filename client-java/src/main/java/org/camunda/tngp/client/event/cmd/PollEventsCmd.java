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
package org.camunda.tngp.client.event.cmd;

import org.camunda.tngp.client.ClientCommand;
import org.camunda.tngp.client.event.EventsBatch;

/**
 * Command to poll events from the broker.
 */
public interface PollEventsCmd extends ClientCommand<EventsBatch>
{

    /**
     * Sets the position the events should be polled from.
     *
     */
    PollEventsCmd startPosition(long position);

    /**
     * Sets the maximum amount of events which should be polled.
     */
    PollEventsCmd maxEvents(int maxEvents);

    /**
     * Sets the id of the topic the events should be polled from.
     */
    PollEventsCmd topicId(int topicId);

}
