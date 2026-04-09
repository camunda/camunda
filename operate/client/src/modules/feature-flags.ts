/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/**
 * Dev-only feature flag: enables the AI Agent Context debug panel
 * in the process instance detail view (BottomPanelTabs).
 *
 * Activate via browser console:  localStorage.setItem('AGENT_CONTEXT_DEBUG', 'true')
 * Only available when Vite is running in development mode.
 */
const IS_AGENT_CONTEXT_DEBUG_ENABLED =
  import.meta.env.DEV &&
  typeof window !== 'undefined' &&
  window.localStorage.getItem('AGENT_CONTEXT_DEBUG') === 'true';

export {IS_AGENT_CONTEXT_DEBUG_ENABLED};
