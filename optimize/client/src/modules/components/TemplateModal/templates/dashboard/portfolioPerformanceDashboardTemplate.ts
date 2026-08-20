/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';

import portfolioPerformance from './images/portfolioPerformance.png';

export function portfolioPerformanceDashboardTemplate() {
  return {
    name: 'portfolioPerformance',
    img: portfolioPerformance,
    disabled: (definitions: unknown[]) => definitions.length < 2,
    config: [
      {
        position: {x: 0, y: 0},
        dimensions: {height: 3, width: 3},
        report: {
          name: t('dashboard.templates.completedProcesses'),
          data: {
            view: {entity: 'processInstance', properties: ['frequency']},
            groupBy: {type: 'none', value: null},
            visualization: 'number',
            filter: [
              {
                appliedTo: ['all'],
                data: null,
                filterLevel: 'instance',
                type: 'completedInstancesOnly',
              },
            ],
          },
        },
        type: 'optimize_report',
      },
      {
        position: {x: 3, y: 0},
        dimensions: {height: 3, width: 3},
        report: {
          name: t('dashboard.templates.activeIncidents'),
          data: {
            view: {entity: 'incident', properties: ['frequency']},
            groupBy: {type: 'none', value: null},
            visualization: 'number',
            filter: [
              {
                appliedTo: ['all'],
                data: null,
                filterLevel: 'instance',
                type: 'includesOpenIncident',
              },
            ],
          },
        },
        type: 'optimize_report',
      },
      {
        position: {x: 6, y: 0},
        dimensions: {height: 3, width: 6},
        report: {
          name: t('dashboard.templates.runningProcesses'),
          data: {
            view: {entity: 'processInstance', properties: ['frequency']},
            groupBy: {type: 'none', value: null},
            distributedBy: {type: 'process', value: null},
            visualization: 'bar',
            filter: [
              {
                appliedTo: ['all'],
                data: null,
                filterLevel: 'instance',
                type: 'runningInstancesOnly',
              },
            ],
          },
        },
        type: 'optimize_report',
      },
      {
        position: {x: 12, y: 0},
        dimensions: {height: 3, width: 6},
        report: {
          name: t('dashboard.templates.runningTasks'),
          data: {
            view: {entity: 'userTask', properties: ['frequency']},
            groupBy: {type: 'userTasks', value: null},
            visualization: 'pie',
            filter: [
              {
                appliedTo: ['all'],
                data: null,
                filterLevel: 'view',
                type: 'runningFlowNodesOnly',
              },
            ],
          },
        },
        type: 'optimize_report',
      },
      {
        position: {x: 0, y: 3},
        dimensions: {height: 5, width: 9},
        report: {
          name: t('dashboard.templates.processTotal'),
          data: {
            view: {entity: 'processInstance', properties: ['frequency']},
            groupBy: {type: 'startDate', value: {unit: 'month'}},
            distributedBy: {type: 'process', value: null},
            visualization: 'bar',
            configuration: {
              stackedBar: true,
            },
          },
        },
        type: 'optimize_report',
      },
      {
        position: {x: 9, y: 3},
        dimensions: {height: 5, width: 9},
        report: {
          name: t('dashboard.templates.laborSavings'),
          data: {
            view: {entity: 'userTask', properties: ['duration']},
            groupBy: {type: 'startDate', value: {unit: 'month'}},
            distributedBy: {type: 'process', value: null},
            visualization: 'bar',
            filter: [
              {
                appliedTo: ['all'],
                data: null,
                filterLevel: 'instance',
                type: 'completedInstancesOnly',
              },
            ],
            configuration: {
              aggregationTypes: [{type: 'sum', value: null}],
              userTaskDurationTimes: ['work'],
              stackedBar: true,
            },
          },
        },
        type: 'optimize_report',
      },
      {
        position: {x: 0, y: 8},
        dimensions: {height: 5, width: 9},
        report: {
          name: t('dashboard.templates.processAcceleration'),
          data: {
            view: {entity: 'processInstance', properties: ['duration']},
            groupBy: {type: 'startDate', value: {unit: 'month'}},
            distributedBy: {type: 'process', value: null},
            visualization: 'line',
          },
        },
        type: 'optimize_report',
      },
      {
        position: {x: 9, y: 8},
        dimensions: {height: 5, width: 9},
        report: {
          name: t('dashboard.templates.taskAutomation'),
          data: {
            view: {entity: 'userTask', properties: ['frequency']},
            groupBy: {type: 'startDate', value: {unit: 'month'}},
            distributedBy: {type: 'process', value: null},
            visualization: 'bar',
            configuration: {
              aggregationTypes: [{type: 'sum', value: null}],
              userTaskDurationTimes: ['work'],
              stackedBar: true,
            },
          },
        },
        type: 'optimize_report',
      },
      {
        position: {x: 0, y: 13},
        dimensions: {height: 5, width: 9},
        report: {
          name: t('dashboard.templates.incidentHandling'),
          data: {
            view: {entity: 'incident', properties: ['duration']},
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'bar',
            filter: [
              {
                appliedTo: ['all'],
                data: null,
                filterLevel: 'view',
                type: 'includesResolvedIncident',
              },
            ],
            configuration: {
              aggregationTypes: [{type: 'avg', value: null}],
              targetValue: {
                active: true,
                isKpi: true,
                durationChart: {unit: 'hours', isBelow: true, value: '1'},
              },
            },
          },
        },
        type: 'optimize_report',
      },
      {
        position: {x: 9, y: 13},
        dimensions: {height: 5, width: 9},
        report: {
          name: t('dashboard.templates.taskLifecycle'),
          data: {
            view: {entity: 'userTask', properties: ['duration']},
            groupBy: {type: 'userTasks', value: null},
            visualization: 'bar',
            configuration: {
              userTaskDurationTimes: ['work', 'idle'],
            },
          },
        },
        type: 'optimize_report',
      },
    ],
  };
}
