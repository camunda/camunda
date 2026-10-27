/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {sortOptions} from './sortOptions';

describe('sortOptions', () => {
  it('should sort alphabetically', () => {
    expect(
      [
        {
          label: 'z',
        },
        {
          label: 'B',
        },
        {
          label: '1',
        },
        {
          label: 'a',
        },
        {
          label: 'X',
        },
      ].sort(sortOptions),
    ).toEqual([
      {
        label: '1',
      },
      {
        label: 'a',
      },
      {
        label: 'B',
      },
      {
        label: 'X',
      },
      {
        label: 'z',
      },
    ]);
  });

  it('should pass through empty array', () => {
    expect([].sort(sortOptions)).toEqual([]);
  });

  it('should keep properties', () => {
    expect([{label: 'foo', name: 'bar'}].sort(sortOptions)).toEqual([
      {label: 'foo', name: 'bar'},
    ]);
  });
});
