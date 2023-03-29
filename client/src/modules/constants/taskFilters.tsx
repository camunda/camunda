/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const taskFilters = {
  'all-open': {
    id: 'all-open',
    text: 'All open',
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
