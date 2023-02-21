/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createReportUpdate, getDefaultSorting} from './reportConfig';

import {getVariableLabel} from 'variables';
import {isCategoricalBar, isCategorical} from '../reportService';

const report = {
  configuration: {
    tableColumns: {},
    customBucket: {},
    distributeByCustomBucket: {},
  },
  definitions: [{}],
  view: {
    entity: 'processInstance',
    properties: ['frequency'],
  },
  groupBy: {
    type: 'startDate',
    value: {unit: 'month'},
  },
  distributedBy: {
    type: 'none',
    value: null,
  },
  visualization: 'bar',
};

jest.mock('variables', () => ({
  getVariableLabel: jest.fn(),
}));

jest.mock('../reportService', () => ({
  isCategoricalBar: jest.fn(),
  isCategorical: jest.fn(),
}));

it('should update the payload when selecting a new report setting', () => {
  expect(createReportUpdate('process', report, 'visualization', 'table').visualization.$set).toBe(
    'table'
  );
});

it('should augment change with custom payload adjustment', () => {
  expect(
    createReportUpdate('process', report, 'group', 'variable', {
      groupBy: {value: {$set: {name: 'boolVar', type: 'Boolean'}}},
    }).groupBy.$set
  ).toEqual({type: 'variable', value: {name: 'boolVar', type: 'Boolean'}});
});

it('should ensure that groups stay valid', () => {
  expect(createReportUpdate('process', report, 'view', 'rawData').groupBy.$set).toEqual({
    type: 'none',
    value: null,
  });
  expect(createReportUpdate('process', report, 'view', 'flowNode').groupBy.$set).toEqual(
    report.groupBy
  );
});

it('should ensure that distributions stay valid', () => {
  const reportWithDistribution = {
    ...report,
    distributedBy: {type: 'variable', value: {name: 'integerVar', type: 'Integer'}},
  };
  expect(
    createReportUpdate('process', reportWithDistribution, 'group', 'duration').distributedBy.$set
  ).toEqual({
    type: 'none',
    value: null,
  });

  expect(
    createReportUpdate('process', reportWithDistribution, 'group', 'endDate').distributedBy.$set
  ).toEqual(reportWithDistribution.distributedBy);
});

it('should ensure that visualizations stay valid', () => {
  expect(createReportUpdate('process', report, 'group', 'none').visualization.$set).toBe('number');
  expect(createReportUpdate('process', report, 'group', 'endDate').visualization.$set).toBe('bar');
});

it('should reset distribution when switching view and distribution is none', () => {
  expect(createReportUpdate('process', report, 'view', 'flowNode').distributedBy.$set).toEqual({
    type: 'flowNode',
    value: null,
  });
});

it('should reset distribution when switching group away from flowNodes', () => {
  const flowNodeReport = {
    ...report,
    view: {entity: 'flowNode', properties: ['frequency']},
    groupBy: {type: 'flowNodes', value: null},
  };
  expect(
    createReportUpdate('process', flowNodeReport, 'group', 'startDate').distributedBy.$set
  ).toEqual({
    type: 'flowNode',
    value: null,
  });
});

it('should update y axis labels', () => {
  expect(
    createReportUpdate('process', report, 'view', 'userTask', {
      view: {properties: {$set: ['duration']}},
    }).configuration.$set.yLabel
  ).toBe('User Task Duration');
});

it('should update x axis labels', () => {
  expect(
    createReportUpdate('process', report, 'group', 'endDate', null, {
      variables: [],
    }).configuration.$set.xLabel
  ).toBe('End Date');

  getVariableLabel.mockReturnValueOnce('boolVarLabel');
  expect(
    createReportUpdate('process', report, 'group', 'variable', {
      groupBy: {value: {$set: {name: 'boolVar', type: 'Boolean'}}},
    }).configuration.$set.xLabel
  ).toBe('boolVarLabel');
});

describe('default sorting', () => {
  it('should sort raw data a descending order by the start date', () => {
    expect(
      createReportUpdate('process', report, 'view', 'rawData').configuration.$set.sorting
    ).toEqual({
      by: 'startDate',
      order: 'desc',
    });
  });

  it('should sort categorical chart reports by value in a descending order', () => {
    isCategorical.mockReturnValueOnce(true);
    expect(
      createReportUpdate('process', report, 'visualization', 'barChart').configuration.$set.sorting
    ).toEqual({
      by: 'value',
      order: 'desc',
    });
  });

  it('should sort number variable by key in ascending order', () => {
    expect(
      createReportUpdate(
        'process',
        {...report, groupBy: {type: 'variable', value: {type: 'Double'}}},
        'visualization',
        'barChart'
      ).configuration.$set.sorting
    ).toEqual({
      by: 'key',
      order: 'asc',
    });
  });

  it('should sort flow node table report by label in ascending order', () => {
    expect(
      createReportUpdate(
        'process',
        {
          ...report,
          view: {entity: 'flowNode', properties: ['frequency']},
          groupBy: {type: 'flowNodes'},
        },
        'visualization',
        'table'
      ).configuration.$set.sorting
    ).toEqual({
      by: 'label',
      order: 'asc',
    });
  });
});

describe('horizonalBarChart', () => {
  it('should keep horizontalBar config as false for non categorical reports', () => {
    isCategoricalBar.mockReturnValueOnce(false);
    expect(
      createReportUpdate('process', report, 'group', 'endDate').configuration.$set.horizontalBar
    ).toBe(false);
  });

  it('should set horizontalBar config to true for catogorical bar chart reports', () => {
    isCategoricalBar.mockReturnValueOnce(true);

    expect(
      createReportUpdate('process', report, 'visualization', 'barChart').configuration.$set
        .horizontalBar
    ).toEqual(true);
  });
});

describe('process exclusive updates', () => {
  it('should reset heatmap target values', () => {
    const heatmapReport = {
      ...report,
      view: {entity: 'flowNode', properties: ['duration']},
      groupBy: {type: 'flowNodes'},
      visualization: 'heat',
      configuration: {
        ...report.configuration,
        heatmapTargetValue: {active: true, values: {flowNode: {value: 12, unit: 'hours'}}},
      },
    };

    expect(
      createReportUpdate('process', heatmapReport, 'visualization', 'barChart').configuration.$set
        .heatmapTargetValue
    ).toEqual({active: false, values: {}});
  });

  it('should remove sum aggregations from incidents', () => {
    const durationReport = {
      ...report,
      view: {entity: 'processInstance', properties: ['duration']},
      configuration: {
        ...report.configuration,
        aggregationTypes: [
          {type: 'avg', value: null},
          {type: 'sum', value: null},
          {type: 'min', value: null},
        ],
      },
    };

    expect(
      createReportUpdate('process', durationReport, 'view', 'incident').configuration.$set
        .aggregationTypes
    ).toEqual([
      {type: 'avg', value: null},
      {type: 'min', value: null},
    ]);
  });

  it('should use average aggregation by default for incident views', () => {
    const durationReport = {
      ...report,
      view: {entity: 'processInstance', properties: ['duration']},
      configuration: {
        ...report.configuration,
        aggregationTypes: [{type: 'sum', value: null}],
      },
    };

    expect(
      createReportUpdate('process', durationReport, 'view', 'incident').configuration.$set
        .aggregationTypes
    ).toEqual([{type: 'avg', value: null}]);
  });

  it('should remove percentile aggregations for group by process reports', () => {
    const processReport = {
      ...report,
      definitions: [{}, {}],
      view: {entity: 'processInstance', properties: ['duration']},
      configuration: {
        ...report.configuration,
        aggregationTypes: [
          {type: 'avg', value: null},
          {type: 'sum', value: null},
          {type: 'percentile', value: 50},
          {type: 'min', value: null},
          {type: 'percentile', value: '95'},
        ],
      },
    };

    expect(
      createReportUpdate('process', processReport, 'group', 'process').configuration.$set
        .aggregationTypes
    ).toEqual([
      {type: 'avg', value: null},
      {type: 'sum', value: null},
      {type: 'min', value: null},
    ]);
  });

  it('should use average aggregation by default for group by process reports', () => {
    const processReport = {
      ...report,
      definitions: [{}, {}],
      view: {entity: 'processInstance', properties: ['duration']},
      configuration: {
        ...report.configuration,
        aggregationTypes: [{type: 'percentile', value: 50}],
      },
    };

    expect(
      createReportUpdate('process', processReport, 'group', 'process').configuration.$set
        .aggregationTypes
    ).toEqual([{type: 'avg', value: null}]);
  });

  it('should remove percentage measure for non process instance reports', () => {
    const processReport = {
      ...report,
      view: {entity: 'processInstance', properties: ['percentage', 'duration']},
      configuration: {
        ...report.configuration,
        aggregationTypes: [{type: 'avg', value: null}],
      },
    };

    expect(
      createReportUpdate('process', processReport, 'view', 'incident').view.$set.properties
    ).toEqual(['duration']);
  });

  it('should use frequency measure by default for non process instance reports', () => {
    const processReport = {
      ...report,
      view: {entity: 'processInstance', properties: ['percentage']},
      configuration: {
        ...report.configuration,
        aggregationTypes: [{type: 'avg', value: null}],
      },
    };

    expect(
      createReportUpdate('process', processReport, 'view', 'incident').view.$set.properties
    ).toEqual(['frequency']);
  });

  it('should remove process parts if report setup does not support it', () => {
    const processPartReport = {
      ...report,
      view: {entity: 'processInstance', properties: ['duration']},
      groupBy: {type: 'none', value: null},
      visualization: 'number',
      configuration: {
        ...report.configuration,
        processPart: {start: 'flowNode1', end: 'flowNode2'},
      },
    };

    expect(
      createReportUpdate('process', processPartReport, 'group', 'startDate').configuration.$set
        .processPart
    ).toEqual(processPartReport.configuration.processPart);

    expect(
      createReportUpdate('process', processPartReport, 'view', 'processInstance', {
        view: {properties: {$set: ['frequency']}},
      }).configuration.$set.processPart
    ).toBe(null);
  });

  it('should remove target values for multi-measure reports', () => {
    const targetValueReport = {
      ...report,
      configuration: {
        ...report.configuration,
        targetValue: {
          active: true,
          countChart: {isBelow: false, value: '10'},
        },
      },
    };

    expect(
      createReportUpdate('process', targetValueReport, 'view', 'processInstance', {
        view: {properties: {$set: ['duration']}},
      }).configuration.$set.targetValue
    ).toEqual(targetValueReport.configuration.targetValue);

    expect(
      createReportUpdate('process', targetValueReport, 'view', 'processInstance', {
        view: {properties: {$set: ['frequency', 'duration']}},
      }).configuration.$set.targetValue.active
    ).toBe(false);
  });

  it('should reset bucket size on group by update', () => {
    const bucketSizeReport = {
      ...report,
      groupBy: {type: 'variable'},
      configuration: {
        ...report.configuration,
        customBucket: {active: true},
        distributeByCustomBucket: {active: true},
      },
    };

    expect(
      createReportUpdate('process', bucketSizeReport, 'group', 'duration').configuration.$set
        .customBucket
    ).toEqual({active: false});
    expect(
      createReportUpdate('process', bucketSizeReport, 'group', 'duration').configuration.$set
        .distributeByCustomBucket
    ).toEqual({active: false});
  });

  it('should not set the default sorting for sorting update', () => {
    expect(
      createReportUpdate('process', report, 'sortingOrder', 'asc').configuration.$set.sorting
    ).toEqual({
      by: 'key',
      order: 'asc',
    });
  });
});

describe('getDefaultSorting', () => {
  it('should sort raw data a descending order by the start date', () => {
    expect(
      getDefaultSorting({reportType: 'process', data: {...report, view: {property: 'rawData'}}})
    ).toEqual({
      by: 'startDate',
      order: 'desc',
    });
  });

  it('should sort categorical chart reports by value in a descending order', () => {
    isCategorical.mockReturnValueOnce(true);
    expect(
      getDefaultSorting({
        reportType: 'process',
        data: {...report, visualization: 'bar'},
      })
    ).toEqual({
      by: 'value',
      order: 'desc',
    });
  });

  it('should sort number variable by key in ascending order', () => {
    expect(
      getDefaultSorting({
        reportType: 'process',
        data: {
          ...report,
          groupBy: {type: 'variable', value: {type: 'Double'}},
          visualization: 'bar',
        },
      })
    ).toEqual({
      by: 'key',
      order: 'asc',
    });
  });

  it('should sort flow node table report by label in ascending order', () => {
    expect(
      getDefaultSorting({
        reportType: 'process',
        data: {
          ...report,
          view: {entity: 'flowNode', properties: ['frequency']},
          groupBy: {type: 'flowNodes'},
          visualization: 'table',
        },
      })
    ).toEqual({
      by: 'label',
      order: 'asc',
    });
  });
});
