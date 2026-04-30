/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {buildInstanceKeyCriterion} from './buildInstanceKeyCriterion';

describe('buildInstanceKeyCriterion', () => {
  it('returns undefined when both arrays are empty/omitted', () => {
    expect(buildInstanceKeyCriterion()).toBeUndefined();
    expect(buildInstanceKeyCriterion([], [])).toBeUndefined();
  });

  it('maps includeIds to $in', () => {
    expect(buildInstanceKeyCriterion(['1', '2'], [])).toEqual({
      $in: ['1', '2'],
    });
  });

  it('maps excludeIds to $notIn', () => {
    expect(buildInstanceKeyCriterion([], ['3', '4'])).toEqual({
      $notIn: ['3', '4'],
    });
  });

  it('combines includeIds and excludeIds', () => {
    expect(buildInstanceKeyCriterion(['1', '2'], ['3'])).toEqual({
      $in: ['1', '2'],
      $notIn: ['3'],
    });
  });

  it('handles single-element arrays', () => {
    expect(buildInstanceKeyCriterion(['only'], ['x'])).toEqual({
      $in: ['only'],
      $notIn: ['x'],
    });
  });
});
