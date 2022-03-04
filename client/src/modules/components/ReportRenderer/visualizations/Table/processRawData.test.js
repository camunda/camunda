/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {parseISO} from 'date-fns';

import {format} from 'dates';
import {NoDataNotice} from 'components';

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
      },
      {
        processInstanceId: 'xyz',
        processDefinitionId: 'abc',
        variables: {
          var1: null,
          var2: true,
        },
      },
    ],
  };

  it('should transform data to table compatible format', () => {
    expect(processRawData({report: {reportType: 'process', data, result}})).toMatchSnapshot();
  });

  it('should not include columns that are hidden', () => {
    const data = {
      configuration: {
        tableColumns: {
          includeNewVariables: false,
          includedColumns: ['processInstanceId'],
          excludedColumns: ['processDefinitionId', 'variable:var1', 'variable:var1'],
          columnOrder: [],
        },
      },
    };
    expect(processRawData({report: {reportType: 'process', data, result}})).toEqual({
      body: [['foo'], ['xyz']],
      head: [{id: 'processInstanceId', label: 'Process Instance Id', title: 'Process Instance Id'}],
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
    expect(processRawData({report: {reportType: 'process', data, result}})).toMatchSnapshot();
  });

  it('should make the processInstanceId a link', () => {
    const cell = processRawData({
      report: {
        reportType: 'process',
        result: {data: [{processInstanceId: '123', engineName: '1'}]},
        data: {
          configuration: {
            tableColumns: {includedColumns: ['processInstanceId', 'engineName'], columnOrder: []},
          },
        },
      },
      camundaEndpoints: {1: {endpoint: 'http://camunda.com', engineName: 'a'}},
    }).body[0][0];

    expect(cell.type).toBe('a');
    expect(cell.props.href).toBe('http://camunda.com/app/cockpit/a/#/process-instance/123');
  });

  it('should format start and end dates', () => {
    // using format here to dynamically return date with client timezone
    const startDate = format(parseISO('2019-06-07'), 'yyyy-MM-dd');
    const endDate = format(parseISO('2019-06-09'), 'yyyy-MM-dd');

    const cells = processRawData({
      report: {
        reportType: 'process',
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
        reportType: 'process',
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

  it('should not make the processInstanceId a link if no endpoint is specified', () => {
    const cell = processRawData({
      report: {
        reportType: 'process',
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
        reportType: 'process',
        data,
        result,
      },
      onVariableView: spy,
    });

    body[0][4].props.onClick();

    const {processInstanceId, processDefinitionKey} = result.data[0];
    expect(spy).toHaveBeenCalledWith('var3', processInstanceId, processDefinitionKey);
  });
});

describe('Decision table', () => {
  const data = {
    configuration: {
      tableColumns: {
        includeNewVariables: true,
        includedColumns: [],
        excludedColumns: [
          'processDefinitionKey',
          'processDefinitionId',
          'processInstanceId',
          'businessKey',
          'startDate',
          'endDate',
          'duration',
          'engineName',
          'tenantId',
          'variable:var1',
          'variable:var2',
        ],
        columnOrder: [],
      },
    },
  };

  it('should show no data message when all column are excluded', () => {
    const result = {
      data: [{decisionInstanceId: 'foo', decisionDefinitionId: 'bar'}],
    };

    expect(processRawData({report: {reportType: 'process', data, result}})).toEqual({
      body: [],
      head: [],
      noData: <NoDataNotice type="info">You need to enable at least one table column</NoDataNotice>,
    });
  });

  it('should show default instances column headers for empty results', () => {
    expect(
      processRawData({report: {reportType: 'process', data, result: {data: []}}})
    ).toMatchSnapshot();
  });

  it('should display decision and process instance ids as links for decision tables', () => {
    const data = {
      configuration: {
        tableColumns: {
          includeNewVariables: false,
          includedColumns: ['decisionInstanceId', 'processInstanceId', 'engineName'],
          excludedColumns: [],
          columnOrder: [],
        },
      },
    };

    const cell = processRawData({
      report: {
        reportType: 'decision',
        result: {
          data: [{decisionInstanceId: '123', processInstanceId: '456', engineName: 'a'}],
        },
        data,
      },
      camundaEndpoints: {a: {endpoint: 'http://camunda.com', engineName: 'a'}},
    }).body[0];

    expect(cell[0].type).toBe('a');
    expect(cell[0].props.href).toBe('http://camunda.com/app/cockpit/a/#/decision-instance/123');
    expect(cell[1].props.href).toBe('http://camunda.com/app/cockpit/a/#/process-instance/456');
  });

  it('should return correct table props', () => {
    const data = {
      configuration: {
        tableColumns: {
          includeNewVariables: false,
          includedColumns: [
            'decisionDefinitionId',
            'decisionInstanceId',
            'processInstanceId',
            'engineName',
            'input:var1',
            'input:var2',
            'output:result',
          ],
          excludedColumns: [],
          columnOrder: [],
        },
      },
    };

    const result = {
      data: [
        {
          decisionInstanceId: 'foo',
          processInstanceId: '456',
          decisionDefinitionId: 'bar',
          engineName: '1',
          inputVariables: {
            var1: {id: 'var1', value: 12, name: 'Var 1'},
            var2: {id: 'var2', value: null, name: 'Var 2'},
          },
          outputVariables: {
            result: {id: 'result', values: [1], name: 'Result'},
          },
        },
        {
          decisionInstanceId: 'xyz',
          processInstanceId: '456',
          decisionDefinitionId: 'abc',
          engineName: '1',
          inputVariables: {
            var1: {id: 'var1', value: null, name: 'Var 1'},
            var2: {id: 'var2', value: true, name: 'Var 2'},
          },
          outputVariables: {
            result: {id: 'result', values: [8], name: 'Result'},
          },
        },
      ],
    };

    expect(processRawData({report: {result, reportType: 'decision', data}})).toMatchSnapshot();
  });

  it('should show no data message when all column are excluded', () => {
    const result = {
      data: [
        {
          decisionInstanceId: 'foo',
          decisionDefinitionId: 'bar',
          inputVariables: {
            var1: {id: 'var1', value: 12, name: 'Var 1'},
          },
          outputVariables: {
            result: {id: 'result', values: [1], name: 'Result'},
          },
        },
      ],
    };
    expect(
      processRawData({
        report: {
          reportType: 'decision',
          result,
          data: {
            configuration: {
              tableColumns: {
                columnOrder: [],
                includeNewVariables: true,
                includedColumns: [],
                excludedColumns: [
                  'decisionDefinitionKey',
                  'decisionDefinitionId',
                  'decisionInstanceId',
                  'processInstanceId',
                  'evaluationDateTime',
                  'engineName',
                  'tenantId',
                  'input:var1',
                  'input:var2',
                  'output:result',
                ],
              },
            },
          },
        },
      })
    ).toEqual({
      body: [],
      head: [],
      noData: <NoDataNotice type="info">You need to enable at least one table column</NoDataNotice>,
    });
  });

  it('should only show instance table column if result data is empty', () => {
    expect(
      processRawData({report: {data, reportType: 'decision', result: {data: []}}})
    ).toMatchSnapshot();
  });
});
