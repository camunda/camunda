/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

type TaskFilter = 'all-open' | 'claimed-by-me' | 'unclaimed' | 'completed';

const OPTIONS: ReadonlyArray<{value: TaskFilter; label: string}> = [
  {
    value: 'all-open',
    label: 'All open',
  },
  {
    value: 'claimed-by-me',
    label: 'Claimed by me',
  },
  {
    value: 'unclaimed',
    label: 'Unclaimed',
  },
  {
    value: 'completed',
    label: 'Completed',
  },
];

export {OPTIONS};
