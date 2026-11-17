/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {buildElementInstanceSort} from './buildElementInstanceSort';

describe('buildElementInstanceSort', () => {
  it('sorts by startDate with elementInstanceKey as a tiebreak, both in the requested direction', () => {
    expect(buildElementInstanceSort('desc')).toEqual([
      {field: 'startDate', order: 'desc'},
      {field: 'elementInstanceKey', order: 'desc'},
    ]);

    expect(buildElementInstanceSort('asc')).toEqual([
      {field: 'startDate', order: 'asc'},
      {field: 'elementInstanceKey', order: 'asc'},
    ]);
  });
});
