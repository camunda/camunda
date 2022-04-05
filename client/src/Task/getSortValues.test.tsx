/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getSortValues} from './getSortValues';
import {tasks} from 'modules/mock-schema/mocks/tasks';

describe('getSortValues', () => {
  it('should get sort values', () => {
    expect(getSortValues()).toBe(undefined);
    expect(getSortValues([])).toBe(undefined);
    expect(getSortValues(tasks)).toBe(undefined);
    expect(getSortValues(tasks.slice(1))).toEqual(['1', '2']);
  });
});
