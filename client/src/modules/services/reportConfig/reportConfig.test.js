/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {updateReport} from './reportConfig';

const report = {
  configuration: {
    tableColumns: {},
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

it('should update the payload when selecting a new report setting', () => {
  expect(updateReport('process', report, 'visualization', 'table').visualization.$set).toBe(
    'table'
  );
});

it('should augment change with custom payload adjustment', () => {
  expect(
    updateReport('process', report, 'group', 'variable', {
      groupBy: {value: {$set: {name: 'boolVar', type: 'Boolean'}}},
    }).groupBy.$set
  ).toEqual({type: 'variable', value: {name: 'boolVar', type: 'Boolean'}});
});

it('should ensure that groups stay valid', () => {
  expect(updateReport('process', report, 'view', 'rawData').groupBy.$set).toEqual({
    type: 'none',
    value: null,
  });
  expect(updateReport('process', report, 'view', 'flowNode').groupBy.$set).toEqual(report.groupBy);
});

it('should ensure that distributions stay valid', () => {
  const reportWithDistribution = {
    ...report,
    distributedBy: {type: 'variable', value: {name: 'integerVar', type: 'Integer'}},
  };
  expect(
    updateReport('process', reportWithDistribution, 'group', 'duration').distributedBy.$set
  ).toEqual({
    type: 'none',
    value: null,
  });

  expect(
    updateReport('process', reportWithDistribution, 'group', 'endDate').distributedBy.$set
  ).toEqual(reportWithDistribution.distributedBy);
});

it('should ensure that visualizations stay valid', () => {
  expect(updateReport('process', report, 'group', 'none').visualization.$set).toBe('number');
  expect(updateReport('process', report, 'group', 'endDate').visualization.$set).toBe('bar');
});

it('should reset distribution when switching view and distribution is none', () => {
  expect(updateReport('process', report, 'view', 'flowNode').distributedBy.$set).toEqual({
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
  expect(updateReport('process', flowNodeReport, 'group', 'startDate').distributedBy.$set).toEqual({
    type: 'flowNode',
    value: null,
  });
});

it('should update y axis labels', () => {
  expect(
    updateReport('process', report, 'view', 'userTask', {view: {properties: {$set: ['duration']}}})
      .configuration.$set.yLabel
  ).toBe('User Task Duration');
});

it('should update x axis labels', () => {
  expect(updateReport('process', report, 'group', 'endDate').configuration.$set.xLabel).toBe(
    'End Date'
  );
  expect(
    updateReport('process', report, 'group', 'variable', {
      groupBy: {value: {$set: {name: 'boolVar', type: 'Boolean'}}},
    }).configuration.$set.xLabel
  ).toBe('boolVar');
});

it('should update sorting', () => {
  expect(updateReport('process', report, 'view', 'rawData').configuration.$set.sorting).toEqual({
    by: 'startDate',
    order: 'desc',
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
      updateReport('process', heatmapReport, 'visualization', 'barChart').configuration.$set
        .heatmapTargetValue
    ).toEqual({active: false, values: {}});
  });

  it('should remove sum aggregations from incidents', () => {
    const durationReport = {
      ...report,
      view: {entity: 'processInstance', properties: ['duration']},
      configuration: {
        ...report.configuration,
        aggregationTypes: ['avg', 'sum', 'min'],
      },
    };

    expect(
      updateReport('process', durationReport, 'view', 'incident').configuration.$set
        .aggregationTypes
    ).toEqual(['avg', 'min']);
  });

  it('should use average aggregation by default for incident views', () => {
    const durationReport = {
      ...report,
      view: {entity: 'processInstance', properties: ['duration']},
      configuration: {
        ...report.configuration,
        aggregationTypes: ['sum'],
      },
    };

    expect(
      updateReport('process', durationReport, 'view', 'incident').configuration.$set
        .aggregationTypes
    ).toEqual(['avg']);
  });

  it('should remove median aggregation for group by process reports', () => {
    const processReport = {
      ...report,
      definitions: [{}, {}],
      view: {entity: 'processInstance', properties: ['duration']},
      configuration: {
        ...report.configuration,
        aggregationTypes: ['avg', 'sum', 'median', 'min'],
      },
    };

    expect(
      updateReport('process', processReport, 'group', 'process').configuration.$set.aggregationTypes
    ).toEqual(['avg', 'sum', 'min']);
  });

  it('should use average aggregation by default for group by process reports', () => {
    const processReport = {
      ...report,
      definitions: [{}, {}],
      view: {entity: 'processInstance', properties: ['duration']},
      configuration: {
        ...report.configuration,
        aggregationTypes: ['median'],
      },
    };

    expect(
      updateReport('process', processReport, 'group', 'process').configuration.$set.aggregationTypes
    ).toEqual(['avg']);
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
      updateReport('process', processPartReport, 'group', 'startDate').configuration.$set
        .processPart
    ).toEqual(processPartReport.configuration.processPart);

    expect(
      updateReport('process', processPartReport, 'view', 'processInstance', {
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
      updateReport('process', targetValueReport, 'view', 'processInstance', {
        view: {properties: {$set: ['duration']}},
      }).configuration.$set.targetValue
    ).toEqual(targetValueReport.configuration.targetValue);

    expect(
      updateReport('process', targetValueReport, 'view', 'processInstance', {
        view: {properties: {$set: ['frequency', 'duration']}},
      }).configuration.$set.targetValue.active
    ).toBe(false);
  });
});
