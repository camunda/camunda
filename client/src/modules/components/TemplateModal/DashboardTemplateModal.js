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
          description: null,
          hasSubtitle: true,
          img: processPerformance,
          disabled: (definitions) => definitions.length > 1,
          config: [
            {
              position: {x: 0, y: 0},
              dimensions: {height: 2, width: 3},
              report: {
                name: t('dashboard.templates.30DayThroughput'),
                data: {
                  view: {entity: 'processInstance', properties: ['frequency']},
                  groupBy: {type: 'none', value: null},
                  visualization: 'number',
                  filter: [
                    {
                      type: 'instanceEndDate',
                      data: {
                        type: 'rolling',
                        start: {
                          value: 30,
                          unit: 'days',
                        },
                      },
                      filterLevel: 'instance',
                      appliedTo: ['all'],
                    },
                  ],
                  configuration: {
                    targetValue: {
                      active: true,
                      isKpi: true,
                      countProgress: {
                        target: '200',
                      },
                    },
                  },
                },
              },
              type: 'optimize_report',
            },
            {
              position: {x: 3, y: 0},
              dimensions: {height: 2, width: 4},
              report: {
                name: t('dashboard.templates.p75Duration'),
                data: {
                  view: {entity: 'processInstance', properties: ['duration']},
                  groupBy: {type: 'none', value: null},
                  visualization: 'number',
                  configuration: {
                    aggregationTypes: [
                      {
                        type: 'percentile',
                        value: 75,
                      },
                    ],
                    precision: 1,
                    targetValue: {
                      active: true,
                      isKpi: true,
                      durationProgress: {
                        target: {
                          unit: 'hours',
                          value: '24',
                          isBelow: true,
                        },
                      },
                    },
                  },
                },
              },
              type: 'optimize_report',
            },
            {
              position: {x: 7, y: 0},
              dimensions: {height: 2, width: 4},
              report: {
                name: t('dashboard.templates.p99Duration'),
                data: {
                  view: {entity: 'processInstance', properties: ['duration']},
                  groupBy: {type: 'none', value: null},
                  visualization: 'number',
                  configuration: {
                    aggregationTypes: [
                      {
                        type: 'percentile',
                        value: 99,
                      },
                    ],
                    precision: 1,
                    targetValue: {
                      active: true,
                      isKpi: true,
                      durationProgress: {
                        target: {
                          unit: 'days',
                          value: '7',
                          isBelow: true,
                        },
                      },
                    },
                  },
                },
              },
              type: 'optimize_report',
            },
            {
              position: {x: 11, y: 0},
              dimensions: {height: 2, width: 3},
              report: {
                name: t('dashboard.templates.percentSLAMet'),
                data: {
                  view: {entity: 'processInstance', properties: ['percentage']},
                  groupBy: {type: 'none', value: null},
                  visualization: 'number',
                  configuration: {
                    targetValue: {
                      active: true,
                      isKpi: true,
                      countProgress: {
                        baseline: '0',
                        target: '99',
                      },
                    },
                  },
                  filter: [
                    {
                      type: 'processInstanceDuration',
                      data: {
                        value: 7,
                        unit: 'days',
                        operator: '<',
                        includeNull: false,
                      },
                      filterLevel: 'instance',
                      appliedTo: ['all'],
                    },
                  ],
                },
              },
              type: 'optimize_report',
            },
            {
              position: {x: 14, y: 0},
              dimensions: {height: 2, width: 4},
              report: {
                name: t('dashboard.templates.percentNoIncidents'),
                data: {
                  view: {entity: 'processInstance', properties: ['percentage']},
                  groupBy: {type: 'none', value: null},
                  visualization: 'number',
                  configuration: {
                    targetValue: {
                      active: true,
                      isKpi: true,
                      countProgress: {
                        baseline: '0',
                        target: '99',
                      },
                    },
                  },
                  filter: [
                    {
                      type: 'doesNotIncludeIncident',
                      data: null,
                      filterLevel: 'instance',
                      appliedTo: ['all'],
                    },
                  ],
                },
              },
              type: 'optimize_report',
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
              type: 'optimize_report',
            },
            {
              position: {x: 9, y: 2},
              dimensions: {height: 5, width: 9},
              report: {
                name: t('dashboard.templates.controlChart'),
                data: {
                  view: {entity: 'processInstance', properties: ['duration']},
                  groupBy: {type: 'startDate', value: {unit: 'week'}},
                  visualization: 'line',
                  configuration: {
                    aggregationTypes: [
                      {type: 'percentile', value: 99},
                      {type: 'percentile', value: 90},
                      {type: 'percentile', value: 75},
                      {type: 'percentile', value: 50},
                    ],
                  },
                },
              },
              type: 'optimize_report',
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
              type: 'optimize_report',
            },
            {
              position: {x: 9, y: 7},
              dimensions: {height: 5, width: 9},
              report: {
                name: t('dashboard.templates.instanceTrends'),
                data: {
                  view: {entity: 'processInstance', properties: ['frequency']},
                  groupBy: {type: 'startDate', value: {unit: 'week'}},
                  visualization: 'bar',
                  configuration: {
                    xLabel: t('report.groupBy.startDate'),
                    yLabel: t('report.view.pi') + ' ' + t('report.view.count'),
                  },
                },
              },
              type: 'optimize_report',
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
              type: 'optimize_report',
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
              type: 'optimize_report',
            },
            {
              position: {x: 0, y: 17},
              dimensions: {height: 5, width: 18},
              report: {
                name: t('dashboard.templates.incidentDurationTrend'),
                data: {
                  view: {entity: 'processInstance', properties: ['frequency', 'duration']},
                  groupBy: {type: 'startDate', value: {unit: 'week'}},
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
              type: 'optimize_report',
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
          description: null,
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
              type: 'optimize_report',
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
              type: 'optimize_report',
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
              type: 'optimize_report',
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
              type: 'optimize_report',
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
              type: 'optimize_report',
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
              type: 'optimize_report',
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
              type: 'optimize_report',
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
              type: 'optimize_report',
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
                      isKpi: true,
                      durationChart: {unit: 'hours', isBelow: true, value: '4'},
                    },
                  },
                },
              },
              type: 'optimize_report',
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
        description: null,
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
            type: 'optimize_report',
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
            type: 'optimize_report',
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
            type: 'optimize_report',
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
            type: 'optimize_report',
          },
        ],
      },
      {
        name: 'humanBottleneckAnalysis',
        description: null,
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
            type: 'optimize_report',
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
            type: 'optimize_report',
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
            type: 'optimize_report',
          },
          {
            position: {x: 6, y: 4},
            dimensions: {height: 4, width: 6},
            report: {
              name: t('dashboard.templates.userTaskImprovement'),
              data: {
                view: {entity: 'userTask', properties: ['duration']},
                groupBy: {type: 'endDate', value: {unit: 'week'}},
                distributedBy: {type: 'userTask', value: null},
                visualization: 'bar',
                configuration: {
                  aggregationTypes: [{type: 'avg', value: null}],
                  userTaskDurationTimes: ['work', 'idle'],
                  stackedBar: true,
                },
              },
            },
            type: 'optimize_report',
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
            type: 'optimize_report',
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
            type: 'optimize_report',
          },
          {
            position: {x: 0, y: 8},
            dimensions: {height: 4, width: 6},
            report: {
              name: t('dashboard.templates.durationImprovement'),
              data: {
                view: {entity: 'processInstance', properties: ['duration']},
                groupBy: {type: 'endDate', value: {unit: 'week'}},
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
            type: 'optimize_report',
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
            type: 'optimize_report',
          },
          {
            position: {x: 12, y: 8},
            dimensions: {height: 4, width: 6},
            report: {
              name: t('dashboard.templates.workDuration'),
              data: {
                view: {entity: 'userTask', properties: ['duration']},
                groupBy: {type: 'endDate', value: {unit: 'week'}},
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
            type: 'optimize_report',
          },
        ],
      }
    );
    templateGroups[2].templates.unshift({
      name: 'portfolioPerformance',
      description: null,
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
