/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {AxeResults} from 'axe-core';
import {isNil} from 'lodash';
import {expect} from '@playwright/test';

function validateResults(results: AxeResults) {
  expect(
    results.violations.filter(
      (violation) =>
        isNil(violation.impact) ||
        ['critical', 'serious'].includes(violation.impact),
    ),
  ).toHaveLength(0);
  expect(results.passes.length).toBeGreaterThan(0);
}

export {validateResults};
