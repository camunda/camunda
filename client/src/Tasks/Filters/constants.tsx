/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {FilterValues} from 'modules/constants/filterValues';

const OPTIONS = {
  [FilterValues.AllOpen]: {
    id: FilterValues.AllOpen,
    text: 'All open',
  },
  [FilterValues.ClaimedByMe]: {
    id: FilterValues.ClaimedByMe,
    text: 'Claimed by me',
  },
  [FilterValues.Unclaimed]: {
    id: FilterValues.Unclaimed,
    text: 'Unclaimed',
  },
  [FilterValues.Completed]: {
    id: FilterValues.Completed,
    text: 'Completed',
  },
} as const;

export {OPTIONS};
