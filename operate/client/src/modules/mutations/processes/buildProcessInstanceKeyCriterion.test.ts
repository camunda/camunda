/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {buildProcessInstanceKeyCriterion} from './buildProcessInstanceKeyCriterion';

describe('buildProcessInstanceKeyCriterion', () => {
  it('returns undefined when both arrays are empty/omitted', () => {
    expect(buildProcessInstanceKeyCriterion()).toBeUndefined();
    expect(buildProcessInstanceKeyCriterion([], [])).toBeUndefined();
  });

  it('maps includeIds to $in', () => {
    expect(buildProcessInstanceKeyCriterion(['1', '2'], [])).toEqual({
      $in: ['1', '2'],
    });
  });

  it('maps excludeIds to $notIn', () => {
    expect(buildProcessInstanceKeyCriterion([], ['3', '4'])).toEqual({
      $notIn: ['3', '4'],
    });
  });

  it('combines includeIds and excludeIds', () => {
    expect(buildProcessInstanceKeyCriterion(['1', '2'], ['3'])).toEqual({
      $in: ['1', '2'],
      $notIn: ['3'],
    });
  });

  it('handles single-element arrays', () => {
    expect(buildProcessInstanceKeyCriterion(['only'], ['x'])).toEqual({
      $in: ['only'],
      $notIn: ['x'],
    });
  });
});
