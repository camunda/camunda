/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import config from './index';

jest.mock('./reportConfig', () => () => ({update: () => ({})}));

describe('process update', () => {
  it('should reset hidden nodes configuration when switching to user task view', () => {
    const changes = config.process.update(
      'view',
      {property: 'duration', entity: 'userTask'},
      {report: {data: {view: {entity: 'flowNode', property: 'duration'}}}}
    );

    expect(changes.configuration.hiddenNodes).toEqual({$set: {active: false, keys: []}});
  });
  it('should not reset hidden nodes configuration when switching between different flow node views', () => {
    const changes = config.process.update(
      'view',
      {property: 'duration', entity: 'flowNode'},
      {report: {data: {view: {entity: 'flowNode', property: 'frequency'}}}}
    );

    expect(changes.configuration.hiddenNodes).not.toBeDefined();
  });

  it('should reset aggregation type if its incompatible outside variable reports', () => {
    const changes = config.process.update(
      'view',
      {property: 'duration', entity: 'processInstance'},
      {report: {data: {configuration: {aggregationType: 'sum'}}}}
    );

    expect(changes.configuration.aggregationType).toEqual({$set: 'avg'});
  });

  it('should set a correct sorting', () => {
    const rawData = config.process.update(
      '',
      {},
      {
        report: {
          reportType: 'decision',
          data: {visualization: 'table', view: {property: 'rawData'}},
        },
      }
    );

    expect(rawData.configuration.sorting).toEqual({
      $set: {by: 'evaluationDateTime', order: 'desc'},
    });

    const userTaskReport = config.process.update(
      '',
      {},
      {
        report: {
          reportType: 'process',
          data: {visualization: 'table', groupBy: {type: 'userTasks'}},
        },
      }
    );

    expect(userTaskReport.configuration.sorting).toEqual({$set: {by: 'label', order: 'asc'}});

    const otherChanges = config.process.update(
      '',
      {},
      {
        report: {
          reportType: 'process',
          data: {visualization: 'table', groupBy: {type: 'variable', value: {type: 'String'}}},
        },
      }
    );

    expect(otherChanges.configuration.sorting).toEqual({$set: {by: 'key', order: 'asc'}});
  });

  it('should keep distributed by compatible when changing group by', () => {
    let changes = config.process.update(
      'groupBy',
      {type: 'assignee'},
      {
        report: {
          data: {
            view: {entity: 'userTask'},
            distributedBy: {type: 'assignee', value: null},
          },
        },
      }
    );

    expect(changes.distributedBy).toEqual({$set: {type: 'none', value: null}});

    changes = config.process.update(
      'groupBy',
      {type: 'duration'},
      {
        report: {
          data: {
            view: {entity: 'userTask'},
            distributedBy: {type: 'assignee', value: null},
          },
        },
      }
    );

    expect(changes.distributedBy).toEqual({$set: {type: 'none', value: null}});

    changes = config.process.update(
      'groupBy',
      {type: 'startDate'},
      {
        report: {
          data: {
            view: {entity: 'userTask'},
            distributedBy: {type: 'assignee', value: null},
          },
        },
      }
    );

    expect(changes.distributedBy).not.toBeDefined();

    changes = config.process.update(
      'groupBy',
      {type: 'runningDate'},
      {
        report: {
          data: {
            view: {entity: 'processInstance'},
            distributedBy: {type: 'variable', value: {}},
          },
        },
      }
    );

    expect(changes.distributedBy).toEqual({$set: {type: 'none', value: null}});

    changes = config.process.update(
      'groupBy',
      {type: 'duration'},
      {
        report: {
          data: {
            view: {entity: 'processInstance'},
            distributedBy: {type: 'startDate', value: {}},
          },
        },
      }
    );

    expect(changes.distributedBy).toEqual({$set: {type: 'none', value: null}});
  });

  it('should keep distributed by compatible when changing view', () => {
    const changes = config.process.update(
      'view',
      {entity: 'flowNode'},
      {
        report: {
          data: {
            view: {entity: 'processInstance'},
            distributedBy: {type: 'variable', value: {}},
          },
        },
      }
    );

    expect(changes.distributedBy).toEqual({$set: {type: 'none', value: null}});
  });
});
