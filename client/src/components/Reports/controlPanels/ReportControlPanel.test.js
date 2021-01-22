/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';

import ReportSelect from './ReportSelect';

import ReportControlPanelWithErrorHandling from './ReportControlPanel';
import {getFlowNodeNames, loadProcessDefinitionXml, loadVariables} from 'services';
import {DefinitionSelection} from 'components';

const ReportControlPanel = ReportControlPanelWithErrorHandling.WrappedComponent;

jest.mock('services', () => {
  const rest = jest.requireActual('services');

  return {
    ...rest,
    loadProcessDefinitionXml: jest.fn().mockReturnValue('I am a process definition xml'),
    loadVariables: jest.fn().mockReturnValue([]),
    reportConfig: {
      process: {
        getLabelFor: () => 'foo',
        options: {
          view: {foo: {data: 'foo', label: 'viewfoo'}},
          groupBy: {
            foo: {data: 'foo', label: 'groupbyfoo'},
            variable: {data: {value: []}, label: 'Variables'},
          },
          visualization: {foo: {data: 'foo', label: 'visualizationfoo'}},
        },
        isAllowed: jest.fn().mockReturnValue(true),
        getNext: jest.fn(),
        update: jest.fn(),
      },
    },
    getFlowNodeNames: jest.fn().mockReturnValue({
      a: 'foo',
      b: 'bar',
    }),
  };
});

const report = {
  data: {
    processDefinitionKey: 'aKey',
    processDefinitionVersions: ['aVersion'],
    tenantIds: [],
    view: {entity: 'processInstance', property: 'frequency'},
    groupBy: {type: 'none', unit: null},
    visualization: 'number',
    filter: [],
    configuration: {xml: 'fooXml'},
  },
  result: {instanceCount: 3},
};

const props = {
  report,
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
};

it('should call the provided updateReport property function when a setting changes', () => {
  const spy = jest.fn();
  const node = shallow(<ReportControlPanel {...props} updateReport={spy} />);

  node.find(ReportSelect).at(0).prop('onChange')('newSetting');

  expect(spy).toHaveBeenCalled();
});

it('should disable the groupBy Select if view is not selected', () => {
  const node = shallow(
    <ReportControlPanel {...props} report={{...report, data: {...report.data, view: ''}}} />
  );

  expect(node.find(ReportSelect).at(1)).toBeDisabled();
});

it('should not disable the groupBy and visualization Selects if view is selected', () => {
  const node = shallow(<ReportControlPanel {...props} />);

  expect(node.find(ReportSelect).at(1)).not.toBeDisabled();
  expect(node.find(ReportSelect).at(2)).not.toBeDisabled();
});

it('should load the variables of the process', () => {
  const node = shallow(<ReportControlPanel {...props} />);

  node.setProps({
    report: {
      ...report,
      data: {...report.data, processDefinitionKey: 'bar', processDefinitionVersions: ['ALL']},
    },
  });

  expect(loadVariables).toHaveBeenCalledWith({
    processDefinitionKey: 'bar',
    processDefinitionVersions: ['ALL'],
    tenantIds: [],
  });
});

it('should include variables in the groupby options', () => {
  const node = shallow(<ReportControlPanel {...props} />);

  const variables = [{name: 'Var1'}, {name: 'Var2'}];
  node.setState({variables});

  const groupbyDropdown = node.find(ReportSelect).at(1);

  expect(groupbyDropdown.prop('variables')).toEqual({variable: variables});
});

it('should not show an "Always show tooltips" button for other visualizations', () => {
  const node = shallow(<ReportControlPanel {...props} visualization="something" />);

  expect(node).not.toIncludeText('Tooltips');
});

it('should load the flownode names and hand them to the filter and process part', async () => {
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={{
        ...report,
        data: {...report.data, view: {entity: 'processInstance', property: 'duration'}},
      }}
    />
  );

  await flushPromises();
  node.update();

  expect(getFlowNodeNames).toHaveBeenCalled();
  expect(node.find('Filter').at(0).prop('flowNodeNames')).toEqual(getFlowNodeNames());
  expect(node.find('ProcessPart').prop('flowNodeNames')).toEqual(getFlowNodeNames());
});

it('should only display process part button if view is process instance duration', () => {
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={{
        ...report,
        data: {...report.data, view: {entity: 'processInstance', property: 'duration'}},
      }}
    />
  );

  expect(node.find('ProcessPart')).toExist();

  node.setProps({
    report: {
      ...report,
      data: {...report.data, view: {entity: 'processInstance', property: 'frequency'}},
    },
  });

  expect(node.find('ProcessPart')).not.toExist();
});

it('should set aggregation to avergage if aggregation is median after setting a process part', () => {
  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel
      {...props}
      updateReport={spy}
      report={{
        ...report,
        data: {
          ...report.data,
          view: {entity: 'processInstance', property: 'duration'},
          configuration: {
            ...report.data.configuration,
            aggregationType: 'median',
          },
        },
      }}
    />
  );

  node.find('ProcessPart').prop('update')({});
  expect(spy).toHaveBeenCalledWith(
    {configuration: {aggregationType: {$set: 'avg'}, processPart: {$set: {}}}},
    true
  );
});

it('should only display target value button if view is flownode duration', () => {
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={{
        ...report,
        data: {
          ...report.data,
          visualization: 'heat',
          view: {entity: 'flowNode', property: 'duration'},
        },
      }}
    />
  );

  expect(node.find('TargetValueComparison')).toExist();

  node.setProps({
    report: {...report, data: {...report.data, view: {entity: 'flowNode', property: 'frequency'}}},
  });

  expect(node.find('TargetValueComparison')).not.toExist();
});

it('should load the process definition xml when a new definition is selected', async () => {
  const spy = jest.fn();
  const node = shallow(<ReportControlPanel {...props} updateReport={spy} />);

  loadProcessDefinitionXml.mockClear();

  await node.find(DefinitionSelection).prop('onChange')({
    key: 'newDefinition',
    versions: ['1'],
    tenantIds: ['a', 'b'],
  });

  expect(loadProcessDefinitionXml).toHaveBeenCalledWith('newDefinition', '1', 'a');
});

it('should remove incompatible filters when changing the process definition', async () => {
  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={{
        ...report,
        data: {
          ...report.data,
          filter: [{type: 'startDate'}, {type: 'executingFlowNodes'}, {type: 'executedFlowNodes'}],
        },
      }}
      updateReport={spy}
    />
  );

  await node.find(DefinitionSelection).prop('onChange')('newDefinition', 1, []);

  expect(spy.mock.calls[0][0].filter.$set).toEqual([{type: 'startDate'}]);
});

it('should reset the groupby and visualization when changing process definition and groupby is variable', async () => {
  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={{...report, data: {...report.data, groupBy: {type: 'variable'}}}}
      updateReport={spy}
    />
  );

  await node.find(DefinitionSelection).prop('onChange')('newDefinition', 1, []);

  expect(spy.mock.calls[0][0].groupBy).toEqual({$set: null});
  expect(spy.mock.calls[0][0].visualization).toEqual({$set: null});
});

it('should reset the view, groupby and visualization when changing process definition and view is variable', async () => {
  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={{
        ...report,
        data: {
          ...report.data,
          view: {
            entity: 'variable',
            property: {name: 'doubleVar', type: 'Double'},
          },
        },
      }}
      updateReport={spy}
    />
  );

  await node.find(DefinitionSelection).prop('onChange')('newDefinition', 1, []);

  expect(spy.mock.calls[0][0].view).toEqual({$set: null});
  expect(spy.mock.calls[0][0].groupBy).toEqual({$set: null});
  expect(spy.mock.calls[0][0].visualization).toEqual({$set: null});
});

it('should reset definition specific configurations on definition change', async () => {
  const spy = jest.fn();
  const node = shallow(<ReportControlPanel {...props} updateReport={spy} />);

  await node.find(DefinitionSelection).prop('onChange')('newDefinition', 1, []);

  expect(spy.mock.calls[0][0].configuration.tableColumns).toBeDefined();
  expect(spy.mock.calls[0][0].configuration.columnOrder).toBeDefined();
  expect(spy.mock.calls[0][0].configuration.heatmapTargetValue).toBeDefined();
  expect(spy.mock.calls[0][0].configuration.processPart).toBeDefined();
});

it('should show the number of process instances in the current Filter', () => {
  const node = shallow(<ReportControlPanel {...props} />);

  expect(node).toIncludeText('3 instances in current filter');
});
