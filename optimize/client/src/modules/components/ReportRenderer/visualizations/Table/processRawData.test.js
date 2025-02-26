/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {parseISO} from 'date-fns';

import {format} from 'dates';

import processRawData, {OBJECT_VARIABLE_IDENTIFIER} from './processRawData';

describe('Process table', () => {
  const data = {
    configuration: {
      tableColumns: {
        includeNewVariables: false,
        includedColumns: [
          'processInstanceId',
          'processDefinitionId',
          'variable:var1',
          'variable:var2',
          'variable:var3',
          'dur:dur1',
          'dur:dur2',
        ],
        excludedColumns: [],
        columnOrder: [],
      },
    },
  };

  const result = {
    data: [
      {
        processInstanceId: 'foo',
        processDefinitionId: 'bar',
        variables: {
          var1: 12,
          var2: null,
          var3: OBJECT_VARIABLE_IDENTIFIER,
        },
        flowNodeDurations: {
          dur1: {name: 'dur1Name', value: null},
          dur2: {name: null, value: 2000},
        },
      },
      {
        processInstanceId: 'xyz',
        processDefinitionId: 'abc',
        variables: {
          var1: null,
          var2: true,
        },
        flowNodeDurations: {
          dur1: {name: 'dur1name', value: 1000},
          dur2: {name: null, value: null},
        },
      },
    ],
  };

  it('should transform data to table compatible format', () => {
    expect(processRawData({report: {data, result}})).toMatchSnapshot();
  });

  it('should not include columns that are hidden', () => {
    const data = {
      configuration: {
        tableColumns: {
          includeNewVariables: false,
          includedColumns: ['processInstanceId'],
          excludedColumns: ['processDefinitionId', 'variable:var1', 'variable:var1', 'dur:dur1'],
          columnOrder: [],
        },
      },
    };
    expect(processRawData({report: {data, result}})).toEqual({
      body: [['foo'], ['xyz']],
      head: [{id: 'processInstanceId', label: 'Process instance Id', title: 'Process instance Id'}],
    });
  });

  it('should exclude variable columns using the variable prefix', () => {
    const data = {
      configuration: {
        tableColumns: {
          includeNewVariables: false,
          includedColumns: ['processInstanceId', 'processDefinitionId', 'variable:var1'],
          excludedColumns: ['variable:var1'],
          columnOrder: [],
        },
      },
    };
    expect(processRawData({report: {data, result}})).toMatchSnapshot();
  });

  it('should format start and end dates', () => {
    // using format here to dynamically return date with client timezone
    const startDate = format(parseISO('2019-06-07'), 'yyyy-MM-dd');
    const endDate = format(parseISO('2019-06-09'), 'yyyy-MM-dd');

    const cells = processRawData({
      report: {
        result: {
          data: [{startDate, endDate}],
        },
        data: {
          configuration: {
            tableColumns: {includedColumns: ['startDate', 'endDate'], columnOrder: []},
          },
        },
      },
    }).body[0];

    const expectedDateFormat = "yyyy-MM-dd HH:mm:ss 'UTC'X";
    expect(cells[0]).toBe(format(parseISO(startDate), expectedDateFormat));
    expect(cells[1]).toBe(format(parseISO(endDate), expectedDateFormat));
  });

  it('should format duration', () => {
    const cells = processRawData({
      report: {
        result: {
          data: [{duration: 123023423}],
        },
        data: {
          configuration: {tableColumns: {includedColumns: ['duration'], columnOrder: []}},
        },
      },
    }).body[0];

    expect(cells[0]).toBe('1d 10h 10min 23s 423ms');
  });

  it('should display the processInstanceId as text', () => {
    const cell = processRawData({
      report: {
        result: {data: [{processInstanceId: '123', engineName: '1'}]},
        data: {
          configuration: {
            tableColumns: {includedColumns: ['processInstanceId', 'engineName'], columnOrder: []},
          },
        },
      },
    }).body[0][0];

    expect(cell).toBe('123');
  });

  it('should invoke the variable view callback function when viewing an object variable', () => {
    const spy = jest.fn();
    const {body} = processRawData({
      report: {
        data,
        result,
      },
      onVariableView: spy,
    });

    body[0][4].props.onClick();

    const {processInstanceId, processDefinitionKey} = result.data[0];
    expect(spy).toHaveBeenCalledWith('var3', processInstanceId, processDefinitionKey);
  });

  it('should disable sorting for flow node duration columns', () => {
    const {head} = processRawData({report: {data, result}});

    const flowNodeDurationColumns = head.filter((column) => column.type === 'flowNodeDurations');

    expect(flowNodeDurationColumns[0].sortable).toBe(false);
  });
});
