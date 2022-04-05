/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getStage} from './getStage';

describe('getStage', () => {
  it('should map the dev stage', () => {
    expect(getStage('dev.ultrawombat.com')).toBe('dev');
  });

  it('should map the int stage', () => {
    expect(getStage('ultrawombat.com')).toBe('int');
  });

  it('should map the prod stage', () => {
    expect(getStage('camunda.io')).toBe('prod');
  });

  it('should handle unknown stages', () => {
    expect(getStage('example.com')).toBe('unknown');
  });
});
