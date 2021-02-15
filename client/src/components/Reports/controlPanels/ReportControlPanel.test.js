/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import update from 'immutability-helper';

import {getFlowNodeNames, loadProcessDefinitionXml, loadVariables} from 'services';
import {DefinitionSelection, Button} from 'components';

import ReportSelect from './ReportSelect';
import ReportControlPanelWithErrorHandling from './ReportControlPanel';

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
    configuration: {xml: 'fooXml', tableColumns: {columnOrder: []}},
  },
  result: {instanceCount: 3, instanceCountWithoutFilters: 5},
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
  shallow(<ReportControlPanel {...props} />);

  expect(loadVariables).toHaveBeenCalledWith({
    processDefinitionKey: 'aKey',
    processDefinitionVersions: ['aVersion'],
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

it('should reset variable groupBy report when changing to a definition that does not have the variable', async () => {
  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={{
        ...report,
        data: {...report.data, groupBy: {type: 'variable', value: {name: 'doubleVar'}}},
      }}
      updateReport={spy}
      setLoading={jest.fn()}
    />
  );

  await node.find(DefinitionSelection).prop('onChange')({});
  expect(spy.mock.calls[0][0].groupBy).toEqual({$set: null});
  expect(spy.mock.calls[0][0].visualization).toEqual({$set: null});
});

it('should reset variable view report when changing to a definition that does not have the variable', async () => {
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
      setLoading={jest.fn()}
    />
  );

  await node.find(DefinitionSelection).prop('onChange')({});

  expect(spy.mock.calls[0][0].view).toEqual({$set: null});
  expect(spy.mock.calls[0][0].groupBy).toEqual({$set: null});
  expect(spy.mock.calls[0][0].visualization).toEqual({$set: null});
});

it('should reset distributed by variable report when changing to a definition that does not have the variable', async () => {
  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={{
        ...report,
        data: {
          ...report.data,
          distributedBy: {type: 'variable', value: {name: 'doubleVar'}},
        },
      }}
      updateReport={spy}
      setLoading={jest.fn()}
    />
  );

  await node.find(DefinitionSelection).prop('onChange')({});

  expect(spy.mock.calls[0][0].distributedBy).toEqual({$set: {type: 'none', value: null}});
});

it('should not reset variable report when changing to a definition that has the same variable', async () => {
  loadVariables.mockReturnValueOnce([{name: 'doubleVar'}]);
  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={{
        ...report,
        data: {...report.data, groupBy: {type: 'variable', value: {name: 'doubleVar'}}},
      }}
      updateReport={spy}
      setLoading={jest.fn()}
    />
  );

  await node.find(DefinitionSelection).prop('onChange')({});
  expect(spy.mock.calls[0][0].groupBy).toEqual(undefined);
});

it('should reset definition specific configurations on definition change', async () => {
  const spy = jest.fn();
  const node = shallow(<ReportControlPanel {...props} updateReport={spy} />);

  await node.find(DefinitionSelection).prop('onChange')({});

  expect(spy.mock.calls[0][0].configuration.heatmapTargetValue).toBeDefined();
  expect(spy.mock.calls[0][0].configuration.processPart).toBeDefined();
});

it('should show the number of process instances in the current Filter', () => {
  const node = shallow(<ReportControlPanel {...props} />);

  expect(node).toIncludeText('Displaying 3 of 5 instances');
});

it('should show a measure selection for views that have a measure', () => {
  const node = shallow(<ReportControlPanel {...props} />);

  expect(node).toIncludeText('Measure');
  expect(node.find('Select').prop('value')).toBe('frequency');
});

it('should show not show a measure selection where it does not make sense', () => {
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={update(props.report, {data: {view: {$set: {entity: null, property: 'rawData'}}}})}
    />
  );

  expect(node).not.toIncludeText('Measure');
});

it('should allow collapsing sections', () => {
  const node = shallow(<ReportControlPanel {...props} />);

  node.find('.source').find(Button).simulate('click');
  expect(node.find('.source')).toHaveClassName('hidden');

  node.find('.source').find(Button).simulate('click');
  expect(node.find('.source')).not.toHaveClassName('hidden');
});

it('should filter non existing variables from columnOrder configuration', async () => {
  loadVariables.mockReturnValueOnce([{name: 'existingVariable'}]);
  const reportWithConfig = {
    data: {
      ...report.data,
      configuration: {
        xml: 'someXml',
        tableColumns: {columnOrder: ['variable:nonExistingVariable', 'variable:existingVariable']},
      },
    },
  };
  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel
      {...props}
      updateReport={spy}
      report={reportWithConfig}
      setLoading={() => {}}
    />
  );

  await node.find(DefinitionSelection).prop('onChange')({});

  expect(spy.mock.calls[0][0].configuration.tableColumns).toEqual({
    columnOrder: {$set: ['variable:existingVariable']},
  });
});
