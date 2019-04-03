/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {processResult} from './service';

it('should process duration reports', () => {
  expect(
    processResult({
      data: {
        view: {
          property: 'duration',
          entity: 'processInstance'
        },
        configuration: {aggregationType: 'max'}
      },
      result: {
        type: 'durationMap',
        data: {
          '2015-03-25T12:00:00Z': {min: 1, median: 2, avg: 3, max: 4},
          '2015-03-26T12:00:00Z': {min: 5, median: 6, avg: 7, max: 8}
        }
      }
    })
  ).toEqual({
    type: 'durationMap',
    data: {
      '2015-03-25T12:00:00Z': 4,
      '2015-03-26T12:00:00Z': 8
    }
  });
});
