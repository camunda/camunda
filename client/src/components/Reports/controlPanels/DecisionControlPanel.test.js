/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import {DefinitionSelection, Button} from 'components';

import {DecisionControlPanel} from './DecisionControlPanel';
import GroupBy from './GroupBy';

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
    definitions: [
      {
        key: 'aKey',
        versions: ['aVersion'],
        tenantIds: [null],
      },
    ],
    view: {property: 'rawData'},
    groupBy: {type: 'none', unit: null},
    visualization: 'table',
    filter: [],
    configuration: {
      xml: 'someXml',
      tableColumns: {columnOrder: [], includedColumns: [], excludedColumns: []},
    },
  },
  result: {instanceCount: 3, instanceCountWithoutFilters: 5},
};

const props = {
  report,
  mightFail: async (data, cb) => cb(await data),
};

beforeEach(() => {
  loadInputVariables.mockClear();
  loadOutputVariables.mockClear();
});

it('should call the provided updateReport property function when a setting changes', () => {
  const spy = jest.fn();
  const node = shallow(<DecisionControlPanel {...props} updateReport={spy} />);

  node.find('View').at(0).prop('onChange')('newSetting');

  expect(spy).toHaveBeenCalled();
});

it('should include variables in the groupby options', () => {
  const node = shallow(<DecisionControlPanel {...props} />);

  const groupbyDropdown = node.find(GroupBy);

  expect(groupbyDropdown.prop('variables')).toBeDefined();
});

it('should retrieve variable names', async () => {
  shallow(<DecisionControlPanel {...props} />);

  const payload = [
    {
      decisionDefinitionKey: 'aKey',
      decisionDefinitionVersions: ['aVersion'],
      tenantIds: [null],
    },
  ];

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

it('should remove non existing variables from columnOrder configuration', async () => {
  loadInputVariables.mockReturnValueOnce([{id: 'existingVariable', name: 'variable name'}]);

  const reportWithConfig = {
    data: {
      ...report.data,
      configuration: {
        xml: 'someXml',
        tableColumns: {
          columnOrder: ['input:nonExistingVariable', 'input:existingVariable'],
          includedColumns: [],
        },
      },
    },
  };

  const spy = jest.fn();
  const node = shallow(
    <DecisionControlPanel
      {...props}
      report={reportWithConfig}
      updateReport={spy}
      setLoading={() => {}}
    />
  );

  await flushPromises();

  await node.find(DefinitionSelection).prop('onChange')({});

  expect(spy.mock.calls[0][0].configuration.tableColumns).toEqual({
    columnOrder: {$set: ['input:existingVariable']},
  });
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

  expect(node).toIncludeText('Displaying data from 3 of 5 evaluations');
});

it('should allow collapsing sections', () => {
  const node = shallow(<DecisionControlPanel {...props} />);

  node.find('.source').find(Button).simulate('click');
  expect(node.find('.source')).toHaveClassName('collapsed');

  node.find('.source').find(Button).simulate('click');
  expect(node.find('.source')).not.toHaveClassName('collapsed');
});

it('should add new variables to includedColumns when switching definition/version', async () => {
  loadOutputVariables.mockReturnValueOnce([{id: 'existingVariable'}, {id: 'newVariable'}]);

  const reportWithConfig = {
    data: {
      ...report.data,
      configuration: {
        xml: 'someXml',
        tableColumns: {
          columnOrder: [],
          includedColumns: ['output:existingVariable'],
        },
      },
    },
  };

  const spy = jest.fn();
  const node = shallow(
    <DecisionControlPanel
      {...props}
      report={reportWithConfig}
      updateReport={spy}
      setLoading={() => {}}
    />
  );

  await flushPromises();

  await node.find(DefinitionSelection).prop('onChange')({});

  expect(spy.mock.calls[0][0].configuration.tableColumns).toEqual({
    includedColumns: {$set: ['output:existingVariable', 'output:newVariable']},
  });
});
