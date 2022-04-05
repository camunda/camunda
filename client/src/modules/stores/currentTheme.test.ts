/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {currentTheme} from './currentTheme';

import {getStateLocally, clearStateLocally} from 'modules/utils/localStorage';

describe('stores/currentTheme', () => {
  beforeEach(() => {
    currentTheme.reset();
    clearStateLocally();
  });

  it('should select the light theme by default', () => {
    expect(currentTheme.state.selectedTheme).toBe('light');
  });

  it('should toggle the selected theme', () => {
    expect(currentTheme.state.selectedTheme).toBe('light');

    currentTheme.toggle();

    expect(currentTheme.state.selectedTheme).toBe('dark');
    expect(getStateLocally().theme).toBe('dark');
  });
});
