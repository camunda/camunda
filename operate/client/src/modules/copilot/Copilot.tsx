/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {CopilotChat, CopilotSidecar, openSidecar} from '@camunda/copilot-chat'
import {useEffect} from 'react'
import {useLocation} from 'react-router-dom'
import {useCopilotAdapter} from './useCopilotAdapter'
import {useCurrentInstanceContext} from './useCurrentInstanceContext'
import {useInstanceSummary} from './useInstanceSummary'
import {getSuggestionsForRoute} from './startingPrompts'
import {setExplainIncidentHandler} from './copilotTriggers'

const INSTANCE_DESCRIPTION =
  'Ask questions about this process instance — understand incidents, trace execution history, and get resolution guidance.'
const GLOBAL_DESCRIPTION =
  'Ask questions about your processes — find incidents, check health, and get insights.'

const Copilot: React.FC = () => {
  const {sendMessage, stopGeneration, resetConversation, isBusy} =
    useCopilotAdapter()
  const {processInstanceId} = useCurrentInstanceContext()
  const summary = useInstanceSummary(processInstanceId)
  const {pathname} = useLocation()
  const suggestions = getSuggestionsForRoute(pathname, {
    hasIncident: summary?.hasIncident,
  })
  const emptyStateDescription =
    processInstanceId !== null ? INSTANCE_DESCRIPTION : GLOBAL_DESCRIPTION

  useEffect(() => {
    setExplainIncidentHandler((incident) => {
      openSidecar()
      const text = incident.errorMessage
        ? `Explain this incident: '${incident.errorMessage}' on '${incident.elementName}'`
        : `Explain this incident on '${incident.elementName}' (errorType: ${incident.errorType})`
      sendMessage(text, {
        ...(processInstanceId !== null ? {processInstanceId} : {}),
        incident,
      })
    })
    return () => setExplainIncidentHandler(null)
  }, [sendMessage, processInstanceId])

  return (
    <CopilotSidecar
      workareaSelector="#main-content"
      headerSelector="header[aria-label='Camunda Operate']"
    >
      {() => (
        <CopilotChat
          onSendMessage={(message) =>
            sendMessage(
              message,
              processInstanceId !== null ? {processInstanceId} : {},
            )
          }
          onStopGeneration={stopGeneration}
          onResetConversation={resetConversation}
          isBusy={isBusy}
          emptyStateTitle="Camunda Copilot"
          emptyStateDescription={emptyStateDescription}
          suggestions={suggestions}
          className="operate-copilot"
        />
      )}
    </CopilotSidecar>
  )
}

export {Copilot}
