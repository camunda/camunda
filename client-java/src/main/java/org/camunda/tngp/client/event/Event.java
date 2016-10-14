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
package org.camunda.tngp.client.event;

import org.agrona.DirectBuffer;

/**
 * Represents one event which has been occurred in the broker.
 */
public interface Event
{

    /**
     * @return the position of the event
     */
    long getPosition();

    /**
     * @return the underlying buffer which contains the event as bytes
     */
    DirectBuffer getRawBuffer();

}
