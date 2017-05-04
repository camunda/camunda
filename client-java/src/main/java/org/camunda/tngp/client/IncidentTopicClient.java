package org.camunda.tngp.client;

import org.camunda.tngp.client.incident.ResolveIncidentCmd;

public interface IncidentTopicClient
{
    /**
     * Resolve an occurred incident by providing a modified payload.
     */
    ResolveIncidentCmd resolve();
}
