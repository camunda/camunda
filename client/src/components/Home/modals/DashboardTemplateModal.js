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
          position: {x: 9, y: 2},
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
          position: {x: 9, y: 7},
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
          position: {x: 0, y: 2},
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
          position: {x: 0, y: 7},
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
