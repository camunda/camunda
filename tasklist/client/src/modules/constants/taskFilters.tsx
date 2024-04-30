/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const taskFilters = {
  'all-open': {
    id: 'all-open',
    text: 'All open tasks',
  },
  'assigned-to-me': {
    id: 'assigned-to-me',
    text: 'Assigned to me',
  },
  unassigned: {
    id: 'unassigned',
    text: 'Unassigned',
  },
  completed: {
    id: 'completed',
    text: 'Completed',
  },
} as const;

export {taskFilters};
