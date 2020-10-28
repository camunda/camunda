/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import reportConfig from './reportConfig';
import * as process from './process';
import * as decision from './decision';

const {
  options: {view, groupBy, visualization},
  getLabelFor,
  isAllowed,
  findSelectedOption,
  update,
} = reportConfig(process);

const report = {data: {distributedBy: {type: 'none', value: null}}};

it('should get a label for a simple visualization', () => {
  expect(getLabelFor('visualization', visualization, 'heat')).toBe('Heatmap');
});

it('should get a label for a complex view', () => {
  expect(getLabelFor('view', view, {entity: 'processInstance', property: 'frequency'})).toBe(
    'Process Instance: Count'
  );
});

it('should get a label for group by variables', () => {
  expect(
    getLabelFor('groupBy', groupBy, {type: 'variable', value: {name: 'aName', type: 'String'}})
  ).toBe('Variable: aName');
});

it('should get a label for group by variables for dmn', () => {
  expect(
    getLabelFor('groupBy', decision.groupBy, {
      type: 'inputVariable',
      value: {id: 'anId', name: 'aName'},
    })
  ).toBe('Input Variable: aName');
});

it('should always allow view selection', () => {
  expect(isAllowed(report, {property: 'rawData', entity: null})).toBe(true);
});

it('should allow only groupBy options that make sense for the selected view', () => {
  expect(
    isAllowed(report, {property: 'rawData', entity: null}, {type: 'none', value: null})
  ).toBeTruthy();
  expect(
    isAllowed(report, {property: 'rawData', entity: null}, {type: 'flowNodes', value: null})
  ).toBeFalsy();
  expect(
    isAllowed(
      report,
      {property: 'frequency', entity: 'processInstance'},
      {type: 'runningDate', value: {unit: 'automatic'}}
    )
  ).toBeTruthy();
  expect(
    isAllowed(
      report,
      {property: 'duration', entity: 'processInstance'},
      {type: 'runningDate', value: {unit: 'automatic'}}
    )
  ).toBeFalsy();
  expect(
    isAllowed(
      report,
      {property: {name: 'doubleVar', type: 'Double'}, entity: 'variable'},
      {type: 'flowNodes', value: null}
    )
  ).toBeFalsy();
  expect(
    isAllowed(
      report,
      {property: {name: 'doubleVar', type: 'Double'}, entity: 'variable'},
      {type: 'none', value: null}
    )
  ).toBeTruthy();
});

it('should allow only visualization options that make sense for the selected view and group', () => {
  expect(
    isAllowed(report, {property: 'rawData', entity: null}, {type: 'none', value: null}, 'table')
  ).toBeTruthy();
  expect(
    isAllowed(report, {property: 'rawData', entity: null}, {type: 'none', value: null}, 'heat')
  ).toBeFalsy();

  expect(
    isAllowed(
      report,
      {
        entity: 'processInstance',
        property: 'duration',
      },
      {
        type: 'startDate',
        value: {
          unit: 'day',
        },
      },
      'pie'
    )
  ).toBeTruthy();
  expect(
    isAllowed(
      report,
      {
        entity: 'processInstance',
        property: 'duration',
      },
      {
        type: 'none',
        value: null,
      },
      'pie'
    )
  ).toBeFalsy();
});

it('should forbid pie charts for distributed user task reports', () => {
  const report = {data: {distributedBy: {type: 'userTask', value: null}}};
  const view = {entity: 'userTask', property: 'frequency'};
  const groupBy = {type: 'assignee', value: null};

  expect(isAllowed(report, view, groupBy, 'bar')).toBeTruthy();
  expect(isAllowed(report, view, groupBy, 'line')).toBeTruthy();
  expect(isAllowed(report, view, groupBy, 'pie')).toBeFalsy();
});

it('should forbid pie charts and heatmap for distributed userTask reports', () => {
  const report = {data: {distributedBy: {type: 'assignee', value: null}}};
  const view = {entity: 'userTask', property: 'frequency'};
  const groupBy = {type: 'userTasks', value: null};

  expect(isAllowed(report, view, groupBy, 'table')).toBeTruthy();
  expect(isAllowed(report, view, groupBy, 'line')).toBeTruthy();
  expect(isAllowed(report, view, groupBy, 'pie')).toBeFalsy();
  expect(isAllowed(report, view, groupBy, 'heat')).toBeFalsy();
});

it('should find a selected option based on property', () => {
  expect(findSelectedOption(view, 'data', {property: 'frequency', entity: 'processInstance'})).toBe(
    view[1].options[0]
  );
  expect(findSelectedOption(groupBy, 'key', 'startDate_day')).toBe(groupBy[4].options[4]);
});

describe('update', () => {
  const countProcessInstances = {
    entity: 'processInstance',
    property: 'frequency',
  };

  const startDate = {
    type: 'startDate',
    value: {unit: 'month'},
  };

  it('should just update visualization', () => {
    expect(update('visualization', 'bar')).toEqual({visualization: {$set: 'bar'}});
  });

  it('should update groupby', () => {
    expect(
      update('groupBy', startDate, {
        report: {
          data: {
            view: countProcessInstances,
            visualization: 'bar',
          },
        },
      })
    ).toEqual({groupBy: {$set: startDate}, configuration: {xLabel: {$set: 'Start Date'}}});
  });

  it("should reset visualization when it's incompatible with the new group", () => {
    expect(
      update('groupBy', startDate, {
        report: {
          data: {
            view: countProcessInstances,
            visualization: 'number',
          },
        },
      }).visualization
    ).toEqual({$set: null});
  });

  it('should automatically select an unambiguous visualization when updating group', () => {
    expect(
      update(
        'groupBy',
        {type: 'none', value: null},
        {
          report: {
            data: {
              view: countProcessInstances,
              visualization: 'heat',
            },
          },
        }
      ).visualization
    ).toEqual({$set: 'number'});
  });

  it('should update view', () => {
    expect(
      update('view', countProcessInstances, {
        report: {
          data: {
            groupBy: startDate,
            visualization: 'bar',
          },
        },
      })
    ).toEqual({
      view: {$set: countProcessInstances},
      configuration: {xLabel: {$set: 'Start Date'}, yLabel: {$set: 'Process Instance Count'}},
    });
  });

  it('should adjust groupby and visualization when changing view', () => {
    expect(
      update('view', countProcessInstances, {
        report: {
          data: {
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'heat',
          },
        },
      })
    ).toMatchSnapshot();
  });
});
