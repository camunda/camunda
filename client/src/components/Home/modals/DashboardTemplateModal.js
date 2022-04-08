/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useEffect} from 'react';

import {t} from 'translation';
import {getOptimizeProfile} from 'config';

import TemplateModal from './TemplateModal';

import processPerformance from './images/processPerformance.png';
import humanPerformance from './images/humanPerformance.png';
import humanBottleneckAnalysis from './images/humanBottleneckAnalysis.png';
import portfolioPerformance from './images/portfolioPerformance.png';
import operationsMonitoring from './images/operationsMonitoring.png';

export default function DashboardTemplateModal({onClose}) {
  const [optimizeProfile, setOptimizeProfile] = useState();
  const [optimizeProfileLoaded, setOptimizeProfileLoaded] = useState(false);

  useEffect(() => {
    (async () => {
      setOptimizeProfile(await getOptimizeProfile());
      setOptimizeProfileLoaded(true);
    })();
  }, []);

  let templateGroups = [
    {
      name: 'blankGroup',
      templates: [{name: 'blank'}],
    },
    {
      name: 'singleProcessGroup',
      templates: [
        {
          name: 'processPerformance',
          hasSubtitle: true,
          img: processPerformance,
          disabled: (definitions) => definitions.length > 1,
          config: [
            {
              position: {x: 0, y: 0},
              dimensions: {height: 2, width: 4},
              report: {
                name: t('dashboard.templates.completedInstances'),
                data: {
                  view: {entity: 'processInstance', properties: ['frequency']},
                  groupBy: {type: 'none', value: null},
                  visualization: 'number',
                  filter: [
                    {
                      appliedTo: ['all'],
                      filterLevel: 'instance',
                      type: 'completedInstancesOnly',
                    },
                  ],
                },
              },
            },
            {
              position: {x: 4, y: 0},
              dimensions: {height: 2, width: 4},
              report: {
                name: t('dashboard.templates.runningInstances'),
                data: {
                  view: {entity: 'processInstance', properties: ['frequency']},
                  groupBy: {type: 'none', value: null},
                  visualization: 'number',
                  filter: [
                    {
                      appliedTo: ['all'],
                      filterLevel: 'instance',
                      type: 'runningInstancesOnly',
                    },
                  ],
                },
              },
            },
            {
              position: {x: 8, y: 0},
              dimensions: {height: 2, width: 6},
              report: {
                name: t('dashboard.templates.aggregateDuration'),
                data: {
                  view: {entity: 'processInstance', properties: ['duration']},
                  groupBy: {type: 'none', value: null},
                  visualization: 'number',
                  configuration: {
                    precision: 3,
                  },
                },
              },
            },
            {
              position: {x: 14, y: 0},
              dimensions: {height: 2, width: 4},
              report: {
                name: t('dashboard.templates.activeIncidents'),
                data: {
                  view: {entity: 'incident', properties: ['frequency']},
                  groupBy: {type: 'none', value: null},
                  visualization: 'number',
                  filter: [
                    {
                      appliedTo: ['all'],
                      filterLevel: 'view',
                      type: 'includesOpenIncident',
                    },
                  ],
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
                  configuration: {
                    aggregationTypes: [
                      {type: 'avg', value: null},
                      {type: 'percentile', value: 50},
                      {type: 'max', value: null},
                    ],
                  },
                },
              },
            },
            {
              position: {x: 9, y: 2},
              dimensions: {height: 5, width: 9},
              report: {
                name: t('dashboard.templates.controlChart'),
                data: {
                  view: {entity: 'processInstance', properties: ['duration']},
                  groupBy: {type: 'startDate', value: {unit: 'automatic'}},
                  visualization: 'line',
                  configuration: {
                    aggregationTypes: [
                      {type: 'min', value: null},
                      {type: 'avg', value: null},
                      {type: 'percentile', value: 50},
                      {type: 'max', value: null},
                    ],
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
            {
              position: {x: 0, y: 12},
              dimensions: {height: 5, width: 9},
              report: {
                name: t('dashboard.templates.activeIncidentsHeatmap'),
                data: {
                  view: {entity: 'incident', properties: ['frequency']},
                  groupBy: {type: 'flowNodes', value: null},
                  visualization: 'heat',
                },
              },
            },
            {
              position: {x: 9, y: 12},
              dimensions: {height: 5, width: 9},
              report: {
                name: t('dashboard.templates.incidentDurationHeatmap'),
                data: {
                  view: {entity: 'incident', properties: ['duration']},
                  groupBy: {type: 'flowNodes', value: null},
                  visualization: 'heat',
                  configuration: {
                    aggregationTypes: [
                      {type: 'avg', value: null},
                      {type: 'percentile', value: 50},
                      {type: 'max', value: null},
                    ],
                  },
                },
              },
            },
            {
              position: {x: 0, y: 17},
              dimensions: {height: 5, width: 18},
              report: {
                name: t('dashboard.templates.incidentDurationTrend'),
                data: {
                  view: {entity: 'processInstance', properties: ['frequency', 'duration']},
                  groupBy: {type: 'startDate', value: {unit: 'automatic'}},
                  visualization: 'barLine',
                  filter: [
                    {
                      appliedTo: ['all'],
                      filterLevel: 'instance',
                      type: 'includesResolvedIncident',
                    },
                  ],
                },
              },
            },
          ],
        },
      ],
    },
    {
      name: 'multiProcessGroup',
      templates: [
        {
          name: 'operationsMonitoring',
          hasSubtitle: true,
          img: operationsMonitoring,
          disabled: (definitions) => definitions.length < 2,
          config: [
            {
              position: {x: 0, y: 0},
              dimensions: {height: 3, width: 4},
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
            },
            {
              position: {x: 4, y: 0},
              dimensions: {height: 3, width: 5},
              report: {
                name: t('dashboard.templates.longRunningProcesses'),
                data: {
                  view: {entity: 'processInstance', properties: ['frequency']},
                  groupBy: {type: 'none', value: null},
                  distributedBy: {type: 'none', value: null},
                  visualization: 'number',
                  filter: [
                    {
                      appliedTo: ['all'],
                      data: null,
                      filterLevel: 'instance',
                      type: 'runningInstancesOnly',
                    },
                    {
                      appliedTo: ['all'],
                      data: {value: 1, unit: 'days', operator: '>', includeNull: false},
                      filterLevel: 'instance',
                      type: 'processInstanceDuration',
                    },
                  ],
                },
              },
            },
            {
              position: {x: 9, y: 0},
              dimensions: {height: 3, width: 4},
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
            },
            {
              position: {x: 13, y: 0},
              dimensions: {height: 3, width: 5},
              report: {
                name: t('dashboard.templates.activeIncidentsByProcess'),
                data: {
                  view: {entity: 'processInstance', properties: ['frequency']},
                  groupBy: {type: 'none', value: null},
                  distributedBy: {type: 'process', value: null},
                  visualization: 'pie',
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
            },
            {
              position: {x: 0, y: 3},
              dimensions: {height: 5, width: 9},
              report: {
                name: t('dashboard.templates.processSnapshot'),
                data: {
                  view: {entity: 'flowNode', properties: ['frequency', 'duration']},
                  groupBy: {type: 'flowNodes', value: null},
                  distributedBy: {type: 'none', value: null},
                  visualization: 'barLine',
                  filter: [
                    {
                      appliedTo: ['all'],
                      data: null,
                      filterLevel: 'view',
                      type: 'runningFlowNodesOnly',
                    },
                  ],
                  configuration: {
                    measureVisualizations: {frequency: 'bar', duration: 'line'},
                    showInstanceCount: true,
                    stackedBar: true,
                  },
                },
              },
            },
            {
              position: {x: 9, y: 3},
              dimensions: {height: 5, width: 9},
              report: {
                name: t('dashboard.templates.incidentSnapshot'),
                data: {
                  view: {entity: 'incident', properties: ['frequency', 'duration']},
                  groupBy: {type: 'flowNodes', value: null},
                  distributedBy: {type: 'none', value: null},
                  visualization: 'barLine',
                  filter: [
                    {
                      appliedTo: ['all'],
                      data: null,
                      filterLevel: 'instance',
                      type: 'includesOpenIncident',
                    },
                    {
                      appliedTo: ['all'],
                      data: null,
                      filterLevel: 'view',
                      type: 'includesOpenIncident',
                    },
                  ],
                  configuration: {
                    measureVisualizations: {frequency: 'bar', duration: 'line'},
                    showInstanceCount: true,
                  },
                },
              },
            },
            {
              position: {x: 0, y: 8},
              dimensions: {height: 5, width: 9},
              report: {
                name: t('dashboard.templates.processHistory'),
                data: {
                  view: {entity: 'processInstance', properties: ['frequency', 'duration']},
                  groupBy: {type: 'startDate', value: {unit: 'week'}},
                  distributedBy: {type: 'process', value: null},
                  visualization: 'barLine',
                  filter: [
                    {
                      appliedTo: ['all'],
                      data: null,
                      filterLevel: 'instance',
                      type: 'completedInstancesOnly',
                    },
                  ],
                  configuration: {
                    measureVisualizations: {frequency: 'bar', duration: 'line'},
                    stackedBar: true,
                  },
                },
              },
            },
            {
              position: {x: 9, y: 8},
              dimensions: {height: 5, width: 9},
              report: {
                name: t('dashboard.templates.incidentHistory'),
                data: {
                  view: {entity: 'processInstance', properties: ['frequency', 'duration']},
                  groupBy: {type: 'startDate', value: {unit: 'week'}},
                  distributedBy: {type: 'process', value: null},
                  visualization: 'barLine',
                  filter: [
                    {
                      appliedTo: ['all'],
                      data: null,
                      filterLevel: 'instance',
                      type: 'includesResolvedIncident',
                    },
                  ],
                  configuration: {
                    measureVisualizations: {frequency: 'bar', duration: 'line'},
                    stackedBar: true,
                    aggregationTypes: [
                      {type: 'avg', value: null},
                      {type: 'max', value: null},
                    ],
                  },
                },
              },
            },
            {
              position: {x: 0, y: 13},
              dimensions: {height: 5, width: 18},
              report: {
                name: t('dashboard.templates.durationSLI'),
                data: {
                  view: {entity: 'processInstance', properties: ['duration']},
                  groupBy: {type: 'startDate', value: {unit: 'week'}},
                  distributedBy: {type: 'process', value: null},
                  visualization: 'line',
                  configuration: {
                    aggregationTypes: [{type: 'max', value: null}],
                    targetValue: {
                      active: true,
                      durationChart: {unit: 'hours', isBelow: true, value: '4'},
                    },
                  },
                },
              },
            },
          ],
        },
      ],
    },
  ];

  if (optimizeProfile === 'platform') {
    templateGroups[1].templates.push(
      {
        name: 'humanPerformance',
        hasSubtitle: true,
        img: humanPerformance,
        disabled: (definitions) => definitions.length > 1,
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
        img: humanBottleneckAnalysis,
        disabled: (definitions) => definitions.length > 1,
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
                  aggregationTypes: [{type: 'avg', value: null}],
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
                  aggregationTypes: [{type: 'avg', value: null}],
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
                  aggregationTypes: [{type: 'avg', value: null}],
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
                  aggregationTypes: [{type: 'avg', value: null}],
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
                  aggregationTypes: [{type: 'avg', value: null}],
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
                  aggregationTypes: [
                    {type: 'avg', value: null},
                    {type: 'percentile', value: 50},
                    {type: 'max', value: null},
                  ],
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
                  aggregationTypes: [{type: 'avg', value: null}],
                  userTaskDurationTimes: ['work'],
                },
              },
            },
          },
        ],
      }
    );
    templateGroups[2].templates.unshift({
      name: 'portfolioPerformance',
      hasSubtitle: true,
      img: portfolioPerformance,
      disabled: (definitions) => definitions.length < 2,
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
        },
        {
          position: {x: 0, y: 3},
          dimensions: {height: 5, width: 9},
          report: {
            name: t('dashboard.templates.processTotal'),
            data: {
              view: {entity: 'processInstance', properties: ['frequency']},
              groupBy: {type: 'startDate', value: {unit: 'automatic'}},
              distributedBy: {type: 'process', value: null},
              visualization: 'bar',
              configuration: {
                stackedBar: true,
              },
            },
          },
        },
        {
          position: {x: 9, y: 3},
          dimensions: {height: 5, width: 9},
          report: {
            name: t('dashboard.templates.laborSavings'),
            data: {
              view: {entity: 'userTask', properties: ['duration']},
              groupBy: {type: 'startDate', value: {unit: 'automatic'}},
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
        },
        {
          position: {x: 0, y: 8},
          dimensions: {height: 5, width: 9},
          report: {
            name: t('dashboard.templates.processAcceleration'),
            data: {
              view: {entity: 'processInstance', properties: ['duration']},
              groupBy: {type: 'startDate', value: {unit: 'automatic'}},
              distributedBy: {type: 'process', value: null},
              visualization: 'line',
            },
          },
        },
        {
          position: {x: 9, y: 8},
          dimensions: {height: 5, width: 9},
          report: {
            name: t('dashboard.templates.taskAutomation'),
            data: {
              view: {entity: 'userTask', properties: ['frequency']},
              groupBy: {type: 'startDate', value: {unit: 'automatic'}},
              distributedBy: {type: 'process', value: null},
              visualization: 'bar',
              configuration: {
                aggregationTypes: [{type: 'sum', value: null}],
                userTaskDurationTimes: ['work'],
                stackedBar: true,
              },
            },
          },
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
                  durationChart: {unit: 'hours', isBelow: true, value: '1'},
                },
              },
            },
          },
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
        },
      ],
    });
  }

  if (!optimizeProfileLoaded) {
    return null;
  }

  return (
    <TemplateModal
      onClose={onClose}
      templateGroups={templateGroups}
      entity="dashboard"
      blankSlate={
        <ol>
          <li>{t('templates.blankSlate.selectProcess')}</li>
          <li>{t('templates.blankSlate.selectTemplate')}</li>
          <li>{t('templates.blankSlate.review')}</li>
          <li>{t('templates.blankSlate.refine')}</li>
        </ol>
      }
      templateToState={({template, ...props}) => ({
        ...props,
        data: template || [],
      })}
    />
  );
}
