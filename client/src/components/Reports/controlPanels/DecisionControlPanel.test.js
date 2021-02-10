/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {DefinitionSelection} from 'components';

import {DecisionControlPanel} from './DecisionControlPanel';
import ReportSelect from './ReportSelect';

import {loadInputVariables, loadOutputVariables} from 'services';

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    loadDecisionDefinitionXml: jest.fn().mockReturnValue('somexml'),
    loadInputVariables: jest.fn().mockReturnValue([]),
    loadOutputVariables: jest.fn().mockReturnValue([]),
    reportConfig: {
      ...rest.reportConfig,
      decision: {
        getLabelFor: () => 'foo',
        options: {
          view: {foo: {data: 'foo', label: 'viewfoo'}},
          groupBy: {
            foo: {data: 'foo', label: 'groupbyfoo'},
            inputVariable: {data: {value: []}, label: 'Input Variable'},
          },
          visualization: {foo: {data: 'foo', label: 'visualizationfoo'}},
        },
        isAllowed: jest.fn().mockReturnValue(true),
        getNext: jest.fn(),
        update: jest.fn(),
      },
    },
  };
});

const report = {
  data: {
    decisionDefinitionKey: 'aKey',
    decisionDefinitionVersions: ['aVersion'],
    tenantIds: [null],
    view: {property: 'rawData'},
    groupBy: {type: 'none', unit: null},
    visualization: 'table',
    filter: [],
    configuration: {
      xml: 'someXml',
    },
  },
  result: {instanceCount: 3},
};

const props = {
  report,
  mightFail: async (data, cb) => cb(await data),
};

it('should call the provided updateReport property function when a setting changes', () => {
  const spy = jest.fn();
  const node = shallow(<DecisionControlPanel {...props} updateReport={spy} />);

  node.find(ReportSelect).at(0).prop('onChange')('newSetting');

  expect(spy).toHaveBeenCalled();
});

it('should disable the groupBy Select if view is not selected', () => {
  const node = shallow(
    <DecisionControlPanel {...props} report={{...report, data: {...report.data, view: ''}}} />
  );

  expect(node.find(ReportSelect).at(1)).toBeDisabled();
});

it('should not disable the groupBy and visualization Selects if view is selected', () => {
  const node = shallow(<DecisionControlPanel {...props} />);

  expect(node.find(ReportSelect).at(1)).not.toBeDisabled();
  expect(node.find(ReportSelect).at(2)).not.toBeDisabled();
});

it('should include variables in the groupby options', () => {
  const node = shallow(<DecisionControlPanel {...props} />);

  const groupbyDropdown = node.find(ReportSelect).at(1);

  expect(groupbyDropdown.prop('variables')).toBeDefined();
});

it('should retrieve variable names', async () => {
  shallow(<DecisionControlPanel {...props} />);

  const payload = {
    decisionDefinitionKey: 'aKey',
    decisionDefinitionVersions: ['aVersion'],
    tenantIds: [null],
  };

  await Promise.resolve();

  expect(loadInputVariables).toHaveBeenCalledWith(payload);
  expect(loadOutputVariables).toHaveBeenCalledWith(payload);
});

it('should reset variable groupby on definition change only if variable does not exist', async () => {
  const spy = jest.fn();
  const node = shallow(
    <DecisionControlPanel
      {...props}
      report={{
        data: {
          ...report.data,
          groupBy: {type: 'inputVariable', value: {id: 'clause1', name: 'Invoice Amount'}},
        },
      }}
      updateReport={spy}
      setLoading={jest.fn()}
    />
  );

  await flushPromises();

  await node.find(DefinitionSelection).prop('onChange')({});
  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].groupBy).toEqual({$set: null});
});

it('should not reset variable report when changing to a definition that has the same variable', async () => {
  loadInputVariables.mockReturnValueOnce([{id: 'clause1', name: 'Invoice Amount'}]);
  const spy = jest.fn();
  const node = shallow(
    <DecisionControlPanel
      {...props}
      report={{
        data: {
          ...report.data,
          groupBy: {type: 'inputVariable', value: {id: 'clause1', name: 'Invoice Amount'}},
        },
      }}
      updateReport={spy}
      setLoading={jest.fn()}
    />
  );

  await flushPromises();

  await node.find(DefinitionSelection).prop('onChange')({});
  expect(spy.mock.calls[0][0].groupBy).toEqual(undefined);
});

it('should reset definition specific configurations on definition change', async () => {
  const spy = jest.fn();
  const node = shallow(<DecisionControlPanel {...props} updateReport={spy} />);

  await node.find(DefinitionSelection).prop('onChange')({});

  expect(spy.mock.calls[0][0].configuration.tableColumns).toBeDefined();
});

it('should not crash when no decisionDefinition is selected', () => {
  shallow(
    <DecisionControlPanel
      {...props}
      report={{
        data: {
          ...report.data,
          decisionDefinitionKey: null,
          decisionDefinitionVersion: null,
          configuration: {...report.data.configuration, xml: null},
        },
      }}
    />
  );
});

it('should show the number of decision instances in the current Filter', () => {
  const node = shallow(<DecisionControlPanel {...props} />);

  expect(node).toIncludeText('3 evaluations in current filter');
});
