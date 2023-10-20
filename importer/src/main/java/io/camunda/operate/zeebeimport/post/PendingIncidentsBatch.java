/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.zeebeimport.post;

import io.camunda.operate.entities.IncidentEntity;
import io.camunda.operate.entities.IncidentState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PendingIncidentsBatch {
    private List<IncidentEntity> incidents = new ArrayList<>();
    private Map<Long, IncidentState> newIncidentStates = new HashMap<>();
    private Long lastProcessedPosition;

    public List<IncidentEntity> getIncidents() {
        return incidents;
    }

    public PendingIncidentsBatch setIncidents(List<IncidentEntity> incidents) {
        this.incidents = incidents;
        return this;
    }

    public Map<Long, IncidentState> getNewIncidentStates() {
        return newIncidentStates;
    }

    public PendingIncidentsBatch setNewIncidentStates(Map<Long, IncidentState> newIncidentStates) {
        this.newIncidentStates = newIncidentStates;
        return this;
    }

    public Long getLastProcessedPosition() {
        return lastProcessedPosition;
    }

    public PendingIncidentsBatch setLastProcessedPosition(Object lastProcessedPosition) {
        if (lastProcessedPosition == null) {
            this.lastProcessedPosition = null;
        } else if (lastProcessedPosition instanceof Integer) {
            this.lastProcessedPosition = Long.valueOf((Integer) lastProcessedPosition);
        } else if (lastProcessedPosition instanceof Long) {
            this.lastProcessedPosition = (Long) lastProcessedPosition;
        } else {
            this.lastProcessedPosition = Long.valueOf(lastProcessedPosition.toString());
        }
        return this;
    }
}
