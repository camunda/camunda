/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
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
      ].sort(sortOptions)
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
