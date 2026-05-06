/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Suggestion} from '@camunda/copilot-chat';

const INSTANCE_PROMPTS: readonly Suggestion[] = [
  {label: 'Why is this stuck?'},
  {label: 'Show recent incidents'},
  {label: 'What variables does it have?'},
];

const PROCESSES_LIST_PROMPTS: readonly Suggestion[] = [
  {label: 'List process definitions'},
  {label: 'Which processes have open incidents?'},
];

const DASHBOARD_PROMPTS: readonly Suggestion[] = [
  {label: 'Which processes are failing?'},
  {label: 'Show me recent incidents across all processes'},
];

const FALLBACK: readonly Suggestion[] = [{label: "What's running right now?"}];

const INSTANCE_PATH = /^\/processes\/[^/]+/;

const getSuggestionsForRoute = (pathname: string): readonly Suggestion[] => {
  if (INSTANCE_PATH.test(pathname)) {
    return INSTANCE_PROMPTS;
  }
  if (pathname === '/processes' || pathname === '/processes/') {
    return PROCESSES_LIST_PROMPTS;
  }
  if (pathname === '/' || pathname === '') {
    return DASHBOARD_PROMPTS;
  }
  return FALLBACK;
};

export {getSuggestionsForRoute};
