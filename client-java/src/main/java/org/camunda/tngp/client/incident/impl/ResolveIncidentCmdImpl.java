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

import static org.camunda.tngp.util.EnsureUtil.ensureGreaterThan;
import static org.camunda.tngp.util.EnsureUtil.ensureNotNull;

import java.io.InputStream;

import org.camunda.tngp.client.impl.ClientCmdExecutor;
import org.camunda.tngp.client.impl.cmd.AbstractExecuteCmdImpl;
import org.camunda.tngp.client.impl.data.MsgPackConverter;
import org.camunda.tngp.client.incident.IncidentResolveResult;
import org.camunda.tngp.client.incident.ResolveIncidentCmd;
import org.camunda.tngp.protocol.clientapi.EventType;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ResolveIncidentCmdImpl extends AbstractExecuteCmdImpl<IncidentEvent, IncidentResolveResult> implements ResolveIncidentCmd
{
    protected final IncidentEvent incidentEvent = new IncidentEvent();
    protected final MsgPackConverter msgPackConverter = new MsgPackConverter();

    protected long incidentKey;

    public ResolveIncidentCmdImpl(ClientCmdExecutor cmdExecutor, ObjectMapper objectMapper, String topicName, int partitionId)
    {
        super(cmdExecutor, objectMapper, IncidentEvent.class, topicName, partitionId, EventType.INCIDENT_EVENT);
    }

    @Override
    public ResolveIncidentCmd incidentKey(long key)
    {
        incidentKey = key;
        return this;
    }

    @Override
    public ResolveIncidentCmd modifiedPayload(InputStream payload)
    {
        this.incidentEvent.setPayload(msgPackConverter.convertToMsgPack(payload));
        return this;
    }

    @Override
    public ResolveIncidentCmd modifiedPayload(String payload)
    {
        this.incidentEvent.setPayload(msgPackConverter.convertToMsgPack(payload));
        return this;
    }

    @Override
    protected Object writeCommand()
    {
        incidentEvent.setEventType(IncidentEventType.RESOLVE);

        return incidentEvent;
    }

    @Override
    protected long getKey()
    {
        return incidentKey;
    }

    @Override
    protected void reset()
    {
        incidentKey = -1L;
        incidentEvent.reset();
    }

    @Override
    protected IncidentResolveResult getResponseValue(int channelId, long key, IncidentEvent event)
    {
        final IncidentResolveResultImpl result = new IncidentResolveResultImpl(key);

        if (event.getEventType() == IncidentEventType.RESOLVE_FAILED)
        {
            result.setResolved(false);
            result.setErrorMessage(event.getErrorMessage());
        }
        else if (event.getEventType() == IncidentEventType.RESOLVE_REJECTED)
        {
            result.setResolved(false);
            result.setErrorMessage("Incident not found or processing a previous request.");
        }

        return result;
    }

    @Override
    public void validate()
    {
        super.validate();
        ensureGreaterThan("incident key", incidentKey, 0);
        ensureNotNull("modified payload", incidentEvent.getPayload());
    }

}
