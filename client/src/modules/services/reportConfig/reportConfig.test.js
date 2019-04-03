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
  getNext,
  update
} = reportConfig(process);

it('should get a label for a simple visualization', () => {
  expect(getLabelFor(visualization, 'heat')).toBe('Heatmap');
});

it('should get a label for a complex view', () => {
  expect(getLabelFor(view, {entity: 'processInstance', property: 'frequency'})).toBe(
    'Process Instance: Count'
  );
});

it('should get a label for group by variables', () => {
  expect(getLabelFor(groupBy, {type: 'variable', value: {name: 'aName', type: 'String'}})).toBe(
    'Variable: aName'
  );
});

it('should get a label for group by variables for dmn', () => {
  expect(
    getLabelFor(
      decision.groupBy,
      {type: 'inputVariable', value: {id: 'anId'}},
      '<definitions><input id="anId" label="aName" /></definitions>'
    )
  ).toBe('Input Variable: aName');
});

it('should return the groupBy based on the view if the groupBy is unambiguous', () => {
  expect(getNext({operation: 'count', entity: 'flowNode', property: 'frequency'})).toEqual({
    type: 'flowNodes'
  });
});

it('should return the visualization based on the view and groupBy if the visualization is unambiguous', () => {
  expect(
    getNext({operation: 'avg', entity: 'processInstance', property: 'duration'}, {type: 'none'})
  ).toEqual('number');
});

it('should return undefined if an unambiguous next config param could not be found', () => {
  expect(getNext({operation: 'avg', entity: 'processInstance', property: 'duration'})).toBe(
    undefined
  );
});

it('should always allow view selection', () => {
  expect(isAllowed({property: 'rawData', entity: null})).toBe(true);
});

it('should allow only groupBy options that make sense for the selected view', () => {
  expect(isAllowed({property: 'rawData', entity: null}, {type: 'none'})).toBe(true);
  expect(isAllowed({property: 'rawData', entity: null}, {type: 'flowNodes'})).toBe(false);
});

it('should allow only visualization options that make sense for the selected view and group', () => {
  expect(isAllowed({property: 'rawData', entity: null}, {type: 'none'}, 'table')).toBe(true);
  expect(isAllowed({property: 'rawData', entity: null}, {type: 'none'}, 'heat')).toBe(false);

  expect(
    isAllowed(
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
  ).toBe(true);
  expect(
    isAllowed(
      {
        entity: 'processInstance',
        property: 'duration'
      },
      {
        type: 'none'
      },
      'pie'
    )
  ).toBe(false);
});

describe('update', () => {
  const countProcessInstances = {
    entity: 'processInstance',
    operation: 'count',
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
        {type: 'none'},
        {
          report: {
            data: {
              view: countProcessInstances,
              visualization: 'heat'
            }
          }
        }
      )
    ).toEqual({groupBy: {$set: {type: 'none'}}, visualization: {$set: 'number'}});
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
            groupBy: {type: 'flowNodes'},
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
