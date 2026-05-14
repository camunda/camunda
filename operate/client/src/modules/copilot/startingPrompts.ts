/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Suggestion} from '@camunda/copilot-chat'

const INSTANCE_INCIDENT_PROMPTS: readonly Suggestion[] = [
  {label: 'Walk me through what happened'},
  {label: 'Why does this instance have an incident?'},
  {label: 'How can I resolve this incident?'},
]

const INSTANCE_HEALTHY_PROMPTS: readonly Suggestion[] = [
  {label: 'Walk me through what happened'},
  {label: 'Where is this instance currently?'},
  {label: 'What variables does it have?'},
]

const GLOBAL_PROMPTS: readonly Suggestion[] = [
  {label: 'Which processes have open incidents?'},
  {label: 'Are my processes healthy?'},
  {label: 'Which processes are running the most instances?'},
]

const FALLBACK_PROMPTS: readonly Suggestion[] = GLOBAL_PROMPTS

const INSTANCE_PATH = /^\/processes\/[^/]+/

interface SuggestionContext {
  readonly hasIncident?: boolean
}

const getSuggestionsForRoute = (
  pathname: string,
  context: SuggestionContext = {},
): readonly Suggestion[] => {
  if (INSTANCE_PATH.test(pathname)) {
    return context.hasIncident
      ? INSTANCE_INCIDENT_PROMPTS
      : INSTANCE_HEALTHY_PROMPTS
  }
  if (pathname === '/processes' || pathname === '/processes/') {
    return GLOBAL_PROMPTS
  }
  if (pathname === '/' || pathname === '') {
    return GLOBAL_PROMPTS
  }
  return FALLBACK_PROMPTS
}

export {getSuggestionsForRoute}
export type {SuggestionContext}
