/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Suggestion} from '@camunda/copilot-chat';
import {matchPath} from 'react-router-dom';

interface RoutePrompts {
  readonly route: string;
  readonly suggestions: readonly Suggestion[];
}

const PROMPTS: readonly RoutePrompts[] = [
  {
    route: '/processes/:processInstanceId/*',
    suggestions: [
      {label: 'Why is this stuck?'},
      {label: 'Show recent incidents'},
      {label: 'What variables does it have?'},
    ],
  },
  {
    route: '/dashboard',
    suggestions: [
      {label: 'Which processes are failing?'},
      {label: 'Show me recent incidents across all processes'},
    ],
  },
  {
    route: '/processes',
    suggestions: [
      {label: 'List process definitions'},
      {label: 'Which processes have open incidents?'},
    ],
  },
];

const FALLBACK: readonly Suggestion[] = [{label: "What's running right now?"}];

const getSuggestionsForRoute = (pathname: string): readonly Suggestion[] => {
  const match = PROMPTS.find((entry) =>
    matchPath({path: entry.route, end: false}, pathname),
  );
  return match?.suggestions ?? FALLBACK;
};

export {getSuggestionsForRoute};
