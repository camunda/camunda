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

/**
 * Represents one workflow definition {@link Event}.
 */
public interface WorkflowDefinitionEvent extends Event
{

    /**
     * @return the workflow definition id
     */
    long getId();

    /**
     * @return the workflow definition key
     */
    String getKey();

    /**
     * @return the workflow definition resource as XML string
     */
    String getResource();

}
