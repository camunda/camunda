/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';

import TemplateModal from './TemplateModal';

import processPerformance from './images/processPerformance.svg';
import humanPerformance from './images/humanPerformance.svg';

export default function DashboardTemplateModal({onClose}) {
  const templates = [
    {name: 'blank'},
    {
      name: 'processPerformance',
      hasSubtitle: true,
      img: processPerformance,
      config: [
        {
          position: {x: 0, y: 0},
          dimensions: {height: 2, width: 4},
          report: {
            name: t('dashboard.templates.totalInstances'),
            data: {
              view: {entity: 'processInstance', properties: ['frequency']},
              groupBy: {type: 'none', value: null},
              visualization: 'number',
            },
          },
        },
        {
          position: {x: 4, y: 0},
          dimensions: {height: 2, width: 14},
          report: {
            name: t('dashboard.templates.avgDuration'),
            data: {
              view: {entity: 'processInstance', properties: ['duration']},
              groupBy: {type: 'none', value: null},
              visualization: 'number',
            },
          },
        },
        {
          position: {x: 0, y: 2},
          dimensions: {height: 5, width: 9},
          report: {
            name: t('dashboard.templates.flownodeDuration'),
            data: {
              view: {entity: 'flowNode', properties: ['duration']},
              groupBy: {type: 'flowNodes', value: null},
              visualization: 'heat',
            },
          },
        },
        {
          position: {x: 9, y: 2},
          dimensions: {height: 5, width: 9},
          report: {
            name: t('dashboard.templates.durationTrends'),
            data: {
              view: {entity: 'processInstance', properties: ['duration']},
              groupBy: {type: 'startDate', value: {unit: 'automatic'}},
              visualization: 'bar',
              configuration: {
                xLabel: t('report.groupBy.startDate'),
                yLabel: t('report.view.pi') + ' ' + t('report.view.duration'),
              },
            },
          },
        },
        {
          position: {x: 0, y: 7},
          dimensions: {height: 5, width: 9},
          report: {
            name: t('dashboard.templates.flownodeFrequency'),
            data: {
              view: {entity: 'flowNode', properties: ['frequency']},
              groupBy: {type: 'flowNodes', value: null},
              visualization: 'heat',
            },
          },
        },
        {
          position: {x: 9, y: 7},
          dimensions: {height: 5, width: 9},
          report: {
            name: t('dashboard.templates.instanceTrends'),
            data: {
              view: {entity: 'processInstance', properties: ['frequency']},
              groupBy: {type: 'startDate', value: {unit: 'automatic'}},
              visualization: 'bar',
              configuration: {
                xLabel: t('report.groupBy.startDate'),
                yLabel: t('report.view.pi') + ' ' + t('report.view.count'),
              },
            },
          },
        },
      ],
    },
    {
      name: 'humanPerformance',
      hasSubtitle: true,
      img: humanPerformance,
      config: [
        {
          position: {x: 0, y: 0},
          dimensions: {height: 5, width: 9},
          report: {
            name: t('dashboard.templates.idleTime'),
            data: {
              view: {entity: 'userTask', properties: ['duration']},
              groupBy: {type: 'userTasks', value: null},
              visualization: 'heat',
              configuration: {userTaskDurationTimes: ['idle']},
            },
          },
        },
        {
          position: {x: 9, y: 0},
          dimensions: {height: 5, width: 9},
          report: {
            name: t('dashboard.templates.tasksStarted'),
            data: {
              view: {entity: 'userTask', properties: ['frequency']},
              groupBy: {type: 'startDate', value: {unit: 'month'}},
              visualization: 'bar',
              distributedBy: {type: 'assignee', value: null},
              configuration: {
                xLabel: t('report.groupBy.startDate'),
                yLabel: t('report.view.userTask') + ' ' + t('report.view.count'),
              },
            },
          },
        },
        {
          position: {x: 0, y: 5},
          dimensions: {height: 5, width: 9},
          report: {
            name: t('dashboard.templates.workTime'),
            data: {
              view: {entity: 'userTask', properties: ['duration']},
              groupBy: {type: 'userTasks', value: null},
              visualization: 'heat',
              configuration: {userTaskDurationTimes: ['work']},
            },
          },
        },
        {
          position: {x: 9, y: 5},
          dimensions: {height: 5, width: 9},
          report: {
            name: t('dashboard.templates.tasksCompleted'),
            data: {
              view: {entity: 'userTask', properties: ['frequency']},
              groupBy: {type: 'endDate', value: {unit: 'month'}},
              visualization: 'bar',
              distributedBy: {type: 'assignee', value: null},
              configuration: {
                xLabel: t('report.groupBy.endDate'),
                yLabel: t('report.view.userTask') + ' ' + t('report.view.count'),
              },
            },
          },
        },
      ],
    },
    {
      name: 'humanBottleneckAnalysis',
      hasSubtitle: true,
      img: processPerformance,
      config: [
        {
          position: {x: 0, y: 0},
          dimensions: {height: 4, width: 9},
          report: {
            name: t('dashboard.templates.bottleneckLocation'),
            data: {
              view: {entity: 'userTask', properties: ['frequency']},
              groupBy: {type: 'userTasks', value: null},
              visualization: 'heat',
              filter: [
                {
                  appliedTo: ['definition'],
                  data: {operator: 'in', values: [null]},
                  filterLevel: 'view',
                  type: 'assignee',
                },
              ],
            },
          },
        },
        {
          position: {x: 9, y: 0},
          dimensions: {height: 4, width: 9},
          report: {
            name: t('dashboard.templates.bottleneckSeverity'),
            data: {
              view: {entity: 'userTask', properties: ['duration']},
              groupBy: {type: 'userTasks', value: null},
              visualization: 'heat',
              configuration: {
                aggregationTypes: ['avg'],
                userTaskDurationTimes: ['total', 'work', 'idle'],
              },
            },
          },
        },
        {
          position: {x: 0, y: 4},
          dimensions: {height: 4, width: 6},
          report: {
            name: t('dashboard.templates.assigneeVariation'),
            data: {
              view: {entity: 'userTask', properties: ['duration']},
              groupBy: {type: 'assignee', value: null},
              distributedBy: {type: 'userTask', value: null},
              filter: [
                {
                  appliedTo: ['definition'],
                  data: {operator: 'not in', values: [null]},
                  filterLevel: 'view',
                  type: 'assignee',
                },
              ],
              visualization: 'bar',
              configuration: {
                aggregationTypes: ['avg'],
                userTaskDurationTimes: ['total'],
                stackedBar: true,
              },
            },
          },
        },
        {
          position: {x: 6, y: 4},
          dimensions: {height: 4, width: 6},
          report: {
            name: t('dashboard.templates.userTaskImprovement'),
            data: {
              view: {entity: 'userTask', properties: ['duration']},
              groupBy: {type: 'endDate', value: {unit: 'automatic'}},
              distributedBy: {type: 'userTask', value: null},
              visualization: 'bar',
              configuration: {
                aggregationTypes: ['avg'],
                userTaskDurationTimes: ['work', 'idle'],
                stackedBar: true,
              },
            },
          },
        },
        {
          position: {x: 12, y: 4},
          dimensions: {height: 4, width: 3},
          report: {
            name: t('dashboard.templates.upstreamWork'),
            data: {
              view: {entity: 'processInstance', properties: ['frequency']},
              groupBy: {type: 'none', value: null},
              visualization: 'number',
              filter: [
                {
                  appliedTo: ['definition'],
                  data: {values: ['StartEvent_1']},
                  filterLevel: 'instance',
                  type: 'executingFlowNodes',
                },
                {
                  appliedTo: ['all'],
                  data: null,
                  filterLevel: 'instance',
                  type: 'runningInstancesOnly',
                },
              ],
              configuration: {
                aggregationTypes: ['avg'],
                userTaskDurationTimes: ['work', 'idle'],
                stackedBar: true,
              },
            },
          },
        },
        {
          position: {x: 15, y: 4},
          dimensions: {height: 4, width: 3},
          report: {
            name: t('dashboard.templates.bottleneckQueue'),
            data: {
              view: {entity: 'processInstance', properties: ['frequency']},
              groupBy: {type: 'none', value: null},
              visualization: 'number',
              filter: [
                {
                  appliedTo: ['definition'],
                  data: {values: ['StartEvent_1']},
                  filterLevel: 'instance',
                  type: 'executingFlowNodes',
                },
                {
                  appliedTo: ['definition'],
                  data: {operator: 'in', values: [null]},
                  filterLevel: 'view',
                  type: 'assignee',
                },
              ],
              configuration: {
                aggregationTypes: ['avg'],
                userTaskDurationTimes: ['work', 'idle'],
                stackedBar: true,
              },
            },
          },
        },
        {
          position: {x: 0, y: 8},
          dimensions: {height: 4, width: 6},
          report: {
            name: t('dashboard.templates.durationImprovement'),
            data: {
              view: {entity: 'processInstance', properties: ['duration']},
              groupBy: {type: 'endDate', value: {unit: 'automatic'}},
              visualization: 'line',
              configuration: {
                aggregationTypes: ['avg', 'median', 'max'],
                userTaskDurationTimes: ['total'],
              },
            },
          },
        },
        {
          position: {x: 6, y: 8},
          dimensions: {height: 4, width: 6},
          report: {
            name: t('dashboard.templates.workerProductivity'),
            data: {
              view: {entity: 'userTask', properties: ['frequency']},
              groupBy: {type: 'assignee', value: null},
              distributedBy: {type: 'userTask', value: null},
              visualization: 'bar',
              filter: [
                {
                  appliedTo: ['all'],
                  data: null,
                  filterLevel: 'view',
                  type: 'completedFlowNodesOnly',
                },
              ],
              configuration: {
                stackedBar: true,
              },
            },
          },
        },
        {
          position: {x: 12, y: 8},
          dimensions: {height: 4, width: 6},
          report: {
            name: t('dashboard.templates.workDuration'),
            data: {
              view: {entity: 'userTask', properties: ['duration']},
              groupBy: {type: 'endDate', value: {unit: 'automatic'}},
              distributedBy: {type: 'userTask', value: null},
              visualization: 'line',
              filter: [
                {
                  appliedTo: ['all'],
                  data: null,
                  filterLevel: 'view',
                  type: 'completedFlowNodesOnly',
                },
              ],
              configuration: {
                aggregationTypes: ['avg'],
                userTaskDurationTimes: ['work'],
              },
            },
          },
        },
      ],
    },
  ];

  return (
    <TemplateModal
      onClose={onClose}
      templates={templates}
      entity="dashboard"
      templateToState={({template, ...props}) => ({
        ...props,
        data: template || [],
      })}
    />
  );
}
