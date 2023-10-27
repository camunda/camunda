/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
