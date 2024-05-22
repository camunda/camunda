/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {convertFilterToDefaultValues, getDefaultFilter} from './service';

const filters = [
  {
    type: 'instanceStartDate',
    data: {type: 'relative', start: {value: 0, unit: 'days'}, end: null},
    filterLevel: 'instance',
  },
  {type: 'runningInstancesOnly', filterLevel: 'instance', data: null},
  {type: 'nonCanceledInstancesOnly', filterLevel: 'instance', data: null},
  {
    type: 'variable',
    data: {name: 'boolVar', type: 'Boolean', data: {values: [false]}},
    filterLevel: 'instance',
  },
  {
    type: 'assignee',
    data: {operator: 'not in', values: ['Themplictur', 'Comentse']},
    filterLevel: 'view',
  },
  {
    type: 'variable',
    data: {
      name: 'dateVar',
      type: 'Date',
      data: {
        type: 'fixed',
        start: '2021-06-08T00:00:00.000+0200',
        end: '2021-07-15T23:59:59.999+0200',
      },
    },
    filterLevel: 'instance',
  },
  {
    type: 'variable',
    data: {
      data: {operator: 'in', values: ['aStringValue', 'b', 'f']},
      name: 'stringVar',
      type: 'String',
    },
    filterLevel: 'instance',
  },
];

describe('convertFilterToDefaultValues', () => {
  it('should extract date filters', () => {
    expect(convertFilterToDefaultValues({type: 'instanceStartDate'}, filters)).toEqual({
      type: 'relative',
      start: {value: 0, unit: 'days'},
      end: null,
    });
  });

  it('should extract assignee filters', () => {
    expect(
      convertFilterToDefaultValues({type: 'assignee', data: {operator: 'not in'}}, filters)
    ).toEqual(['Themplictur', 'Comentse']);
  });

  it('should extract instance state filters', () => {
    expect(convertFilterToDefaultValues({type: 'state'}, filters)).toEqual([
      'runningInstancesOnly',
      'nonCanceledInstancesOnly',
    ]);
  });

  it('should extract boolean variable filters', () => {
    expect(
      convertFilterToDefaultValues(
        {type: 'variable', data: {name: 'boolVar', type: 'Boolean'}},
        filters
      )
    ).toEqual([false]);
  });

  it('should extract date variable filters', () => {
    expect(
      convertFilterToDefaultValues(
        {type: 'variable', data: {name: 'dateVar', type: 'Date'}},
        filters
      )
    ).toEqual({
      type: 'fixed',
      start: '2021-06-08T00:00:00.000+0200',
      end: '2021-07-15T23:59:59.999+0200',
    });
  });

  it('should extract string variable filters', () => {
    expect(
      convertFilterToDefaultValues(
        {type: 'variable', data: {name: 'stringVar', type: 'String', data: {operator: 'in'}}},
        filters
      )
    ).toEqual(['aStringValue', 'b', 'f']);
  });
});

describe('getDefaultFilter', () => {
  it('should construct an array of report filters from availableFilters', () => {
    expect(
      getDefaultFilter([
        {
          type: 'instanceStartDate',
          data: {
            defaultValues: {
              type: 'relative',
              start: {
                value: 0,
                unit: 'days',
              },
              end: null,
            },
          },
        },
        {
          type: 'state',
          data: {
            defaultValues: ['runningInstancesOnly', 'nonCanceledInstancesOnly'],
          },
        },
        {
          type: 'variable',
          data: {
            name: 'boolVar',
            type: 'Boolean',
            defaultValues: [false],
          },
        },
        {
          type: 'assignee',
          data: {
            operator: 'not in',
            values: ['Plasoner', 'Themplictur'],
            allowCustomValues: true,
            defaultValues: ['Themplictur', 'Comentse'],
          },
        },
        {
          type: 'variable',
          data: {
            name: 'dateVar',
            type: 'Date',
            defaultValues: {
              type: 'fixed',
              start: '2021-06-08T00:00:00.000+0200',
              end: '2021-07-15T23:59:59.999+0200',
            },
          },
        },
        {
          type: 'variable',
          data: {
            data: {
              operator: 'in',
              values: ['aStringValue', 'b'],
              allowCustomValues: true,
            },
            name: 'stringVar',
            type: 'String',
            defaultValues: ['aStringValue', 'b', 'f'],
          },
        },
      ])
    ).toEqual(filters);
  });
});
