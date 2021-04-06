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
      {properties: ['duration'], entity: 'userTask'},
      {report: {data: {view: {entity: 'flowNode', properties: ['duration']}, configuration: {}}}}
    );

    expect(changes.configuration.hiddenNodes).toEqual({$set: {active: false, keys: []}});
  });
  it('should not reset hidden nodes configuration when switching between different flow node views', () => {
    const changes = config.process.update(
      'view',
      {properties: ['duration'], entity: 'flowNode'},
      {
        report: {
          data: {
            view: {entity: 'flowNode', properties: ['frequency']},
            groupBy: {},
            configuration: {},
          },
        },
      }
    );

    expect(changes.configuration.hiddenNodes).not.toBeDefined();
  });

  it('should reset aggregation type if its incompatible outside variable reports', () => {
    const changes = config.process.update(
      'view',
      {properties: ['duration'], entity: 'processInstance'},
      {report: {data: {configuration: {aggregationTypes: ['sum']}}, configuration: {}}}
    );

    expect(changes.configuration.aggregationTypes).toEqual({$set: ['avg']});
  });

  it('should always reset tabel column order', () => {
    const changes = config.process.update(
      'view',
      {properties: ['duration'], entity: 'processInstance'},
      {report: {data: {configuration: {aggregationTypes: ['sum']}}, configuration: {}}}
    );

    expect(changes.configuration.tableColumns.columnOrder).toEqual({$set: []});
  });

  it('should set a correct sorting', () => {
    const rawData = config.process.update(
      '',
      {},
      {
        report: {
          reportType: 'decision',
          data: {visualization: 'table', view: {properties: ['rawData']}},
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
            view: {entity: 'userTask', properties: ['frequency']},
            distributedBy: {type: 'assignee', value: null},
          },
        },
      }
    );

    expect(changes.distributedBy).toEqual({$set: {type: 'userTask', value: null}});

    changes = config.process.update(
      'groupBy',
      {type: 'startDate'},
      {
        report: {
          data: {
            view: {entity: 'userTask', properties: ['frequency']},
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
            view: {entity: 'processInstance', properties: ['frequency']},
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
            view: {entity: 'processInstance', properties: ['frequency']},
            distributedBy: {type: 'startDate', value: {}},
          },
        },
      }
    );

    expect(changes.distributedBy).toEqual({$set: {type: 'none', value: null}});
  });

  it('should keep distributed by compatible when changing view', () => {
    let changes = config.process.update(
      'view',
      {entity: 'flowNode', properties: ['frequency']},
      {
        report: {
          data: {
            view: {entity: 'processInstance', properties: ['frequency']},
            distributedBy: {type: 'variable', value: {}},
            configuration: {},
          },
        },
      }
    );
    expect(changes.distributedBy).toEqual({$set: {type: 'none', value: null}});

    changes = config.process.update(
      'view',
      {entity: 'flowNode', properties: ['duration']},
      {
        report: {
          data: {
            view: {entity: 'flowNode', properties: ['frequency']},
            groupBy: {type: 'duration'},
            distributedBy: {type: 'flowNode', value: null},
            configuration: {},
          },
        },
      }
    );

    expect(changes.distributedBy).toEqual({$set: {type: 'flowNode', value: null}});
  });

  it('should automatically distribute by flownode/usertask when possible', () => {
    const changes = config.process.update(
      'groupBy',
      {type: 'duration'},
      {
        report: {
          data: {
            view: {entity: 'userTask', properties: ['frequency']},
            distributedBy: {type: 'assignee', value: null},
          },
        },
      }
    );

    expect(changes.distributedBy).toEqual({$set: {type: 'userTask', value: null}});
  });

  it('should not automatically distribute by flownode/usertask if it was explicitely set to none', () => {
    const changes = config.process.update(
      'groupBy',
      {type: 'startDate'},
      {
        report: {
          data: {
            view: {entity: 'userTask', properties: ['frequency']},
            distributedBy: {type: 'none'},
          },
        },
      }
    );

    expect(changes.distributedBy).not.toBeDefined();
  });

  it('should deactivate target value when switching to multi-measure', () => {
    const changes = config.process.update(
      'view',
      {properties: ['frequency', 'duration'], entity: 'processInstance'},
      {
        report: {
          data: {
            configuration: {targetValue: {active: true, countProgress: {}, durationProgress: {}}},
          },
        },
      }
    );

    expect(changes.configuration.targetValue).toEqual({active: {$set: false}});
  });
});

describe('decision update', () => {
  it('should always reset tabel column order', () => {
    const changes = config.decision.update(
      'view',
      {properties: ['frequency'], property: 'frequency'},
      {report: {data: {visualization: 'table', view: {properties: ['rawData']}}}}
    );

    expect(changes.configuration.tableColumns.columnOrder).toEqual({$set: []});
  });
});
