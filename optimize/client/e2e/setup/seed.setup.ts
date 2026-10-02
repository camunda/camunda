/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect, test as setup} from '@playwright/test';

import {expectedImportState, readImportState} from '../seed/importState';
import {createCamunda, ensureSeeded} from '../seed/seed';

setup('seed data and wait for the Optimize import', async () => {
  setup.setTimeout(5 * 60_000);

  await ensureSeeded(createCamunda());

  await expect
    .poll(readImportState, {
      message:
        'Optimize did not import the seeded data. Reset the stack if the seed was interrupted.',
      timeout: 4 * 60_000,
      intervals: [2_000],
    })
    .toEqual(expectedImportState());
});
