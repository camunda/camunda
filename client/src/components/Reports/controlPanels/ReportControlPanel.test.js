/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {shallow} from 'enzyme';
import update from 'immutability-helper';

import {getFlowNodeNames, loadProcessDefinitionXml, loadVariables} from 'services';

import {DefinitionList} from './DefinitionList';
import GroupBy from './GroupBy';
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
    definitions: [
      {
        key: 'aKey',
        versions: ['aVersion'],
        tenantIds: [],
      },
    ],
    view: {entity: 'processInstance', properties: ['frequency']},
    groupBy: {type: 'none', unit: null},
    distributedBy: {type: 'none', unit: null},
    visualization: 'number',
    filter: [],
    configuration: {
      xml: 'fooXml',
      tableColumns: {columnOrder: [], includedColumns: [], excludedColumns: []},
      heatmapTargetValue: {values: {}},
      customBucket: {active: false},
      distributeByCustomBucket: {active: false},
      targetValue: {isKpi: false},
    },
  },
  result: {instanceCount: 3, instanceCountWithoutFilters: 5},
};

const props = {
  report,
  mightFail: jest.fn().mockImplementation((data, cb) => cb(data)),
  setLoading: () => {},
};

beforeEach(() => {
  loadVariables.mockClear();
});

it('should call the provided updateReport property function when a setting changes', () => {
  const spy = jest.fn();
  const node = shallow(<ReportControlPanel {...props} updateReport={spy} />);

  node.find('View').prop('onChange')('newSetting');

  expect(spy).toHaveBeenCalled();
});

it('should load the variables of the process', () => {
  shallow(<ReportControlPanel {...props} />);

  expect(loadVariables).toHaveBeenCalledWith([
    {
      processDefinitionKey: 'aKey',
      processDefinitionVersions: ['aVersion'],
      tenantIds: [],
    },
  ]);
});

it('should include variables in the groupby options', () => {
  const node = shallow(<ReportControlPanel {...props} />);

  const variables = [{name: 'Var1'}, {name: 'Var2'}];
  node.setState({variables});

  const groupbyDropdown = node.find(GroupBy);

  expect(groupbyDropdown.prop('variables')).toEqual({variable: variables});
});

it('should not show an "Always show tooltips" button for other visualizations', () => {
  const node = shallow(<ReportControlPanel {...props} visualization="something" />);

  expect(node).not.toIncludeText('Tooltips');
});

it('should load the flownode names and hand them to the process part', async () => {
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={{
        ...report,
        data: {...report.data, view: {entity: 'processInstance', properties: ['duration']}},
      }}
    />
  );

  await flushPromises();
  node.update();

  expect(getFlowNodeNames).toHaveBeenCalled();
  expect(node.find('ProcessPart').prop('flowNodeNames')).toEqual(getFlowNodeNames());
});

it('should only display process part button if view is process instance duration', () => {
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={{
        ...report,
        data: {...report.data, view: {entity: 'processInstance', properties: ['duration']}},
      }}
    />
  );

  expect(node.find('ProcessPart')).toExist();

  node.setProps({
    report: {
      ...report,
      data: {...report.data, view: {entity: 'processInstance', properties: ['frequency']}},
    },
  });

  expect(node.find('ProcessPart')).not.toExist();
});

it('should remove percentile aggregations after setting a process part', () => {
  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel
      {...props}
      updateReport={spy}
      report={{
        ...report,
        data: {
          ...report.data,
          view: {entity: 'processInstance', properties: ['duration']},
          configuration: {
            ...report.data.configuration,
            aggregationTypes: [
              {type: 'min', value: null},
              {type: 'max', value: null},
              {type: 'percentile', value: 50},
              {type: 'percentile', value: 95},
            ],
          },
        },
      }}
    />
  );

  node.find('ProcessPart').prop('update')({});
  expect(spy.mock.calls[0][0].configuration.aggregationTypes.$set).toEqual([
    {type: 'min', value: null},
    {type: 'max', value: null},
  ]);

  node.setProps({
    report: {
      ...report,
      data: {
        ...report.data,
        view: {entity: 'processInstance', properties: ['duration']},
        configuration: {
          ...report.data.configuration,
          aggregationTypes: [{type: 'percentile', value: 50}],
        },
      },
    },
  });

  node.find('ProcessPart').prop('update')({});
  expect(spy.mock.calls[1][0].configuration.aggregationTypes.$set).toEqual([
    {type: 'avg', value: null},
  ]);
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
          view: {entity: 'flowNode', properties: ['duration']},
        },
      }}
    />
  );

  expect(node.find('TargetValueComparison')).toExist();

  node.setProps({
    report: {
      ...report,
      data: {...report.data, view: {entity: 'flowNode', properties: ['frequency']}},
    },
  });

  expect(node.find('TargetValueComparison')).not.toExist();
});

it('should load the process definition xml when a new definition is selected', async () => {
  const spy = jest.fn();
  const node = shallow(<ReportControlPanel {...props} updateReport={spy} />);

  loadProcessDefinitionXml.mockClear();

  await node.find(DefinitionList).prop('onChange')(
    {
      key: 'newDefinition',
      versions: ['1'],
      tenantIds: ['a', 'b'],
    },
    0
  );

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
    />
  );

  await node.find(DefinitionList).prop('onChange')({}, 0);

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
            properties: [{name: 'doubleVar', type: 'Double'}],
          },
        },
      }}
      updateReport={spy}
    />
  );

  await node.find(DefinitionList).prop('onChange')({}, 0);

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
    />
  );

  await node.find(DefinitionList).prop('onChange')({}, 0);

  expect(spy.mock.calls[0][0].distributedBy).toEqual({$set: {type: 'none', value: null}});
});

it('should not reset variable report when changing to a definition that has the same variable', async () => {
  loadVariables.mockReturnValue([{name: 'doubleVar'}]);
  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={{
        ...report,
        data: {...report.data, groupBy: {type: 'variable', value: {name: 'doubleVar'}}},
      }}
      updateReport={spy}
    />
  );

  await node.find(DefinitionList).prop('onChange')({}, 0);
  expect(spy.mock.calls[0][0].groupBy).toEqual(undefined);
});

it('should reset heatmap target value on definition change if flow nodes does not exist', async () => {
  const reportWithConfig = update(report, {
    data: {
      configuration: {
        heatmapTargetValue: {
          values: {
            $set: {
              nonExistingFlowNode: {},
            },
          },
        },
      },
    },
  });

  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel {...props} updateReport={spy} report={reportWithConfig} />
  );

  await node.find(DefinitionList).prop('onChange')({}, 0);

  expect(spy.mock.calls[0][0].configuration.heatmapTargetValue).toBeDefined();
});

it('should reset process part on definition change if flow nodes does not exist', async () => {
  const reportWithConfig = update(report, {
    data: {
      configuration: {
        processPart: {
          $set: {
            start: 'nonExistingFlowNode',
            end: 'nonExistingFlowNode',
          },
        },
      },
    },
  });

  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel {...props} updateReport={spy} report={reportWithConfig} />
  );

  await node.find(DefinitionList).prop('onChange')({}, 0);

  expect(spy.mock.calls[0][0].configuration.processPart).toBeDefined();
});

it('should not reset heatmap target value on definition change if flow nodes exits', async () => {
  loadVariables.mockReturnValueOnce([{name: 'existingFlowNode'}]);
  const reportWithConfig = update(report, {
    data: {
      configuration: {
        heatmapTargetValue: {
          values: {
            $set: {
              a: {},
            },
          },
        },
      },
    },
  });

  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel {...props} updateReport={spy} report={reportWithConfig} />
  );

  await node.find(DefinitionList).prop('onChange')({}, 0);

  expect(spy.mock.calls[0][0].configuration.heatmapTargetValue).not.toBeDefined();
});

it('should reset heatmap visualization when copying a definition', () => {
  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={update(report, {data: {visualization: {$set: 'heat'}}})}
      updateReport={spy}
    />
  );

  node.find(DefinitionList).prop('onCopy')(0);

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].visualization).toEqual({$set: 'table'});
});

it('should not reset process part on definition change if flow nodes exist', async () => {
  const reportWithConfig = update(report, {
    data: {
      configuration: {
        processPart: {
          $set: {
            start: 'a',
            end: 'b',
          },
        },
      },
    },
  });

  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel {...props} updateReport={spy} report={reportWithConfig} />
  );

  await node.find(DefinitionList).prop('onChange')({}, 0);

  expect(spy.mock.calls[0][0].configuration.processPart).not.toBeDefined();
});

it('should show the number of process instances in the current Filter', () => {
  const node = shallow(<ReportControlPanel {...props} />);

  expect(node).toIncludeText('Displaying data from 3 of 5 instances');
});

it('should display an astrick near the total instance count if the report includes date filters', () => {
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={update(props.report, {
        data: {filter: {$set: [{type: 'instanceStartDate'}]}},
      })}
    />
  );

  expect(node).toIncludeText('Displaying data from 3 of *5 instances');
});

it('should show a measure selection for views that have a measure', () => {
  const node = shallow(<ReportControlPanel {...props} />);

  expect(node.find('Measure')).toExist();
  expect(node.find('Measure').prop('report')).toBe(props.report.data);
});

it('should show not show a measure selection where it does not make sense', () => {
  const node = shallow(
    <ReportControlPanel
      {...props}
      report={update(props.report, {data: {view: {$set: {entity: null, properties: ['rawData']}}}})}
    />
  );

  expect(node.find('Measure')).not.toExist();
});

it('should allow collapsing sections', () => {
  const node = shallow(<ReportControlPanel {...props} />);

  node.find('.source .sectionTitle').simulate('click');
  expect(node.find('.source')).toHaveClassName('collapsed');

  node.find('.source .sectionTitle').simulate('click');
  expect(node.find('.source')).not.toHaveClassName('collapsed');
});

it('should reset columnOrder only when changing definition', async () => {
  const reportWithConfig = update(report, {
    data: {
      definitions: {0: {key: {$set: 'original'}}},
      configuration: {
        tableColumns: {
          columnOrder: {
            $set: ['col1', 'col2'],
          },
        },
      },
    },
  });

  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel {...props} updateReport={spy} report={reportWithConfig} />
  );

  await node.find(DefinitionList).prop('onChange')({key: 'differentDefinition'}, 0);

  expect(spy.mock.calls[0][0].configuration.tableColumns).toEqual({
    columnOrder: {$set: []},
  });
});

it('should not reset columnOrder when changing version', async () => {
  const reportWithConfig = update(report, {
    data: {
      definitions: {0: {key: {$set: 'same'}}},
      configuration: {
        tableColumns: {
          columnOrder: {
            $set: ['col1', 'col2'],
          },
        },
      },
    },
  });

  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel {...props} updateReport={spy} report={reportWithConfig} />
  );

  await node.find(DefinitionList).prop('onChange')({key: 'same'}, 0);

  expect(spy.mock.calls[0][0].configuration.tableColumns).not.toBeDefined();
});

it('should add new variables to includedColumns when switching definition/version', async () => {
  loadVariables.mockReturnValue([{name: 'existingVariable'}, {name: 'newVariable'}]);
  const reportWithConfig = update(report, {
    data: {
      configuration: {
        tableColumns: {
          includedColumns: {$set: ['variable:existingVariable']},
        },
      },
    },
  });

  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel {...props} updateReport={spy} report={reportWithConfig} />
  );

  await node.find(DefinitionList).prop('onChange')({}, 0);

  expect(spy.mock.calls[0][0].configuration.tableColumns.includedColumns).toEqual({
    $set: ['variable:existingVariable', 'variable:newVariable'],
  });
});

it('should call updateReport with correct payload when adding measures', () => {
  const spy = jest.fn();
  const node = shallow(<ReportControlPanel {...props} updateReport={spy} />);

  node.find('Measure').simulate('change', 'newMeasure');

  expect(spy).toHaveBeenCalledWith('newMeasure', true);
});

it('should remove distribute by process if going back to single process report', async () => {
  const spy = jest.fn();

  const node = shallow(
    <ReportControlPanel
      {...props}
      report={update(props.report, {
        data: {
          definitions: {
            $set: [
              {
                key: 'aKey',
                versions: ['aVersion'],
                tenantIds: [],
                identifier: 'def1',
              },
              {
                key: 'aKey',
                versions: ['aVersion'],
                tenantIds: [],
                identifier: 'def2',
              },
            ],
          },
          distributedBy: {$set: {type: 'process', value: null}},
        },
      })}
      updateReport={spy}
    />
  );

  await node.find(DefinitionList).prop('onRemove')(0);

  expect(spy.mock.calls[0][0].distributedBy).toEqual({
    $set: {type: 'none', value: null},
  });
});

describe('filter handling when changing definitions', () => {
  const report = update(props.report, {
    data: {
      definitions: {
        $set: [
          {
            key: 'aKey',
            versions: ['aVersion'],
            tenantIds: [],
            identifier: 'def1',
          },
          {
            key: 'aKey',
            versions: ['aVersion'],
            tenantIds: [],
            identifier: 'def2',
          },
        ],
      },
      filter: {
        $set: [
          {filterLevel: 'instance', type: 'runningInstancesOnly', appliedTo: ['def1', 'def2']},
        ],
      },
    },
  });

  it('should remove removed definitions from filters', async () => {
    const spy = jest.fn();
    const node = shallow(<ReportControlPanel {...props} report={report} updateReport={spy} />);

    await node.find(DefinitionList).prop('onRemove')(0);

    expect(spy.mock.calls[0][0].filter).toEqual({
      $set: [{filterLevel: 'instance', type: 'runningInstancesOnly', appliedTo: ['def2']}],
    });
  });

  it('should remove filters if they contain no definitions after definition removal', async () => {
    const spy = jest.fn();
    const node = shallow(
      <ReportControlPanel
        {...props}
        report={update(report, {data: {filter: {0: {appliedTo: {$set: ['def1']}}}}})}
        updateReport={spy}
      />
    );

    await node.find(DefinitionList).prop('onRemove')(0);

    expect(spy.mock.calls[0][0].filter).toEqual({
      $set: [],
    });
  });
});

it('should disable bucket size configuration when changing definitions', async () => {
  const reportWithConfig = update(report, {
    data: {
      configuration: {
        customBucket: {active: {$set: true}},
        distributeByCustomBucket: {active: {$set: true}},
      },
    },
  });

  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel {...props} updateReport={spy} report={reportWithConfig} />
  );

  await node.find(DefinitionList).prop('onChange')({}, 0);

  expect(spy.mock.calls[0][0].configuration.customBucket).toEqual({active: {$set: false}});
  expect(spy.mock.calls[0][0].configuration.distributeByCustomBucket).toEqual({
    active: {$set: false},
  });
});

it('should reset kpi status when having more than one definition', async () => {
  const reportWithConfig = update(report, {
    data: {
      definitions: {
        $set: [
          {
            key: 'aKey',
            versions: ['aVersion'],
            tenantIds: [],
            identifier: 'def1',
          },
          {
            key: 'aKey',
            versions: ['aVersion'],
            tenantIds: [],
            identifier: 'def2',
          },
        ],
      },
      configuration: {
        targetValue: {
          isKpi: {
            $set: true,
          },
        },
      },
    },
  });

  const spy = jest.fn();
  const node = shallow(
    <ReportControlPanel {...props} updateReport={spy} report={reportWithConfig} />
  );

  await node.find(DefinitionList).prop('onChange')({}, 0);

  expect(spy.mock.calls[0][0].configuration.targetValue.isKpi).toBeDefined();
});

it('should use key as a displayName for copied process when it has no displayName and name properties', () => {
  const spy = jest.fn();
  const node = shallow(<ReportControlPanel {...props} updateReport={spy} report={report} />);

  node.find(DefinitionList).prop('onCopy')(0);

  expect(spy).toHaveBeenCalled();
  expect(spy.mock.calls[0][0].definitions.$splice[0][2].displayName).toBe('aKey (copy)');
});

it('should show Sorting with proper values for reports grouped by date', () => {
  const reportWithGRoupByDate = update(report, {data: {groupBy: {$set: {type: 'startDate'}}}});
  const node = shallow(<ReportControlPanel {...props} report={reportWithGRoupByDate} />);

  expect(node.find('Sorting').prop('report')).toBe(reportWithGRoupByDate.data);
  expect(node.find('Sorting').prop('type')).toBe('process');
});
