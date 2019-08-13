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
  update
} = reportConfig(process);

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
      value: {id: 'anId', name: 'aName'}
    })
  ).toBe('Input Variable: aName');
});

it('should always allow view selection', () => {
  expect(isAllowed(null, {property: 'rawData', entity: null})).toBe(true);
});

it('should allow only groupBy options that make sense for the selected view', () => {
  expect(
    isAllowed(null, {property: 'rawData', entity: null}, {type: 'none', value: null})
  ).toBeTruthy();
  expect(
    isAllowed(null, {property: 'rawData', entity: null}, {type: 'flowNodes', value: null})
  ).toBeFalsy();
});

it('should allow only visualization options that make sense for the selected view and group', () => {
  expect(
    isAllowed(null, {property: 'rawData', entity: null}, {type: 'none', value: null}, 'table')
  ).toBeTruthy();
  expect(
    isAllowed(null, {property: 'rawData', entity: null}, {type: 'none', value: null}, 'heat')
  ).toBeFalsy();

  expect(
    isAllowed(
      null,
      {
        entity: 'processInstance',
        property: 'duration'
      },
      {
        type: 'startDate',
        value: {
          unit: 'day'
        }
      },
      'pie'
    )
  ).toBeTruthy();
  expect(
    isAllowed(
      null,
      {
        entity: 'processInstance',
        property: 'duration'
      },
      {
        type: 'none',
        value: null
      },
      'pie'
    )
  ).toBeFalsy();
});

it('should forbid line and pie charts for distributed user task reports', () => {
  const report = {data: {configuration: {distributedBy: 'userTask'}}};
  const view = {entity: 'userTask', property: 'frequency'};
  const groupBy = {type: 'assignee', value: null};

  expect(isAllowed(report, view, groupBy, 'bar')).toBeTruthy();
  expect(isAllowed(report, view, groupBy, 'line')).toBeFalsy();
  expect(isAllowed(report, view, groupBy, 'pie')).toBeFalsy();
});

describe('update', () => {
  const countProcessInstances = {
    entity: 'processInstance',
    property: 'frequency'
  };

  const startDate = {
    type: 'startDate',
    value: {unit: 'month'}
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
            visualization: 'bar'
          }
        }
      })
    ).toEqual({groupBy: {$set: startDate}});
  });

  it("should reset visualization when it's incompatible with the new group", () => {
    expect(
      update('groupBy', startDate, {
        report: {
          data: {
            view: countProcessInstances,
            visualization: 'number'
          }
        }
      })
    ).toEqual({groupBy: {$set: startDate}, visualization: {$set: null}});
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
              visualization: 'heat'
            }
          }
        }
      )
    ).toEqual({groupBy: {$set: {type: 'none', value: null}}, visualization: {$set: 'number'}});
  });

  it('should update view', () => {
    expect(
      update('view', countProcessInstances, {
        report: {
          data: {
            groupBy: startDate,
            visualization: 'bar'
          }
        }
      })
    ).toEqual({view: {$set: countProcessInstances}});
  });

  it('should adjust groupby and visualization when changing view', () => {
    expect(
      update('view', countProcessInstances, {
        report: {
          data: {
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'heat'
          }
        }
      })
    ).toEqual({
      view: {$set: countProcessInstances},
      groupBy: {$set: null},
      visualization: {$set: null}
    });
  });
});
