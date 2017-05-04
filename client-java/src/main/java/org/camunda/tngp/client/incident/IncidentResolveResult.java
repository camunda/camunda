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
package org.camunda.tngp.client.incident;

/**
 * The result of an incident resolve command.
 */
public interface IncidentResolveResult
{
    /**
     * @return the key of the incident
     */
    long getIncidentKey();

    /**
     * @return <code>true</code>, if the incident is resolved.
     */
    boolean isIncidentResolved();

    /**
     * @return the error message, in case the incident could not be resolved.
     */
    String getErrorMessage();
}
