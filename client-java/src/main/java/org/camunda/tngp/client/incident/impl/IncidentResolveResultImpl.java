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
package org.camunda.tngp.client.incident.impl;

import org.camunda.tngp.client.incident.IncidentResolveResult;

public class IncidentResolveResultImpl implements IncidentResolveResult
{
    protected final long incidentKey;

    protected boolean isResolved = true;
    protected String errorMessage = "";

    public IncidentResolveResultImpl(long incidentKey)
    {
        this.incidentKey = incidentKey;
    }

    @Override
    public long getIncidentKey()
    {
        return incidentKey;
    }

    @Override
    public boolean isIncidentResolved()
    {
        return isResolved;
    }

    @Override
    public String getErrorMessage()
    {
        return errorMessage;
    }

    public void setResolved(boolean isResolved)
    {
        this.isResolved = isResolved;
    }

    public void setErrorMessage(String errorMessage)
    {
        this.errorMessage = errorMessage;
    }

}
