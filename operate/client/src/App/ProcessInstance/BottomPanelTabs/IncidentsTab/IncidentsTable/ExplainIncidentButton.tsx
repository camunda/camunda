/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react'
import {requestExplainIncident} from 'modules/copilot/copilotTriggers'
import CopilotSparkle from 'modules/copilot/icons/CopilotSparkle.svg?react'
import {tracking} from 'modules/tracking'
import type {EnhancedIncident} from 'modules/hooks/incidents'

interface Props {
  readonly incident: EnhancedIncident
}

const ExplainIncidentButton: React.FC<Props> = ({incident}) => (
  <Button
    size="sm"
    kind="ghost"
    renderIcon={CopilotSparkle}
    onClick={(event) => {
      event.stopPropagation()
      tracking.track({
        eventName: 'incident-explain-clicked',
        errorType: String(incident.errorType),
      })
      requestExplainIncident({
        errorMessage: incident.errorMessage ?? '',
        elementName: incident.elementName ?? incident.elementId ?? '',
        errorType: String(incident.errorType),
        incidentKey: String(incident.incidentKey),
        elementId: incident.elementId ?? '',
      })
    }}
  >
    Explain
  </Button>
)

export {ExplainIncidentButton}
