/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {FilterValues} from 'modules/constants/filterValues';

const OPTIONS = [
  {
    value: FilterValues.AllOpen,
    label: 'All open',
  },
  {
    value: FilterValues.ClaimedByMe,
    label: 'Claimed by me',
  },
  {
    value: FilterValues.Unclaimed,
    label: 'Unclaimed',
  },
  {
    value: FilterValues.Completed,
    label: 'Completed',
  },
] as const;

export {OPTIONS};
