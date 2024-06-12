/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
