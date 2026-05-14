/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

interface ExplainIncidentRequest {
  readonly errorMessage: string
  readonly elementName: string
  readonly errorType: string
  readonly incidentKey: string
  readonly elementId: string
}

type ExplainIncidentHandler = (request: ExplainIncidentRequest) => void

let handler: ExplainIncidentHandler | null = null

/**
 * Decouples the in-table "Explain incident" button from the Copilot adapter
 * (which lives at the Layout level). The Copilot component registers a
 * handler on mount; any consumer can call requestExplainIncident to dispatch
 * an explain action without needing the adapter directly.
 */
const setExplainIncidentHandler = (next: ExplainIncidentHandler | null): void => {
  handler = next
}

const requestExplainIncident = (request: ExplainIncidentRequest): void => {
  handler?.(request)
}

export {requestExplainIncident, setExplainIncidentHandler}
export type {ExplainIncidentHandler, ExplainIncidentRequest}
