/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'translation';

import operationsMonitoring from './images/operationsMonitoring.png';

export function operationsMonitoringDashboardTemplate() {
  return {
    name: 'operationsMonitoring',
    img: operationsMonitoring,
    disabled: (definitions: unknown[]) => definitions.length < 2,
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
  };
}
