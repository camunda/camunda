/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {t} from 'translation';

import TemplateModal from './TemplateModal';

import heatmapImg from './images/heatmap.png';
import durationImg from './images/duration.png';
import durationProgress from './images/durationProgress.png';
import percentageProgress from './images/percentageProgress.png';
import tableImg from './images/table.png';
import chartImg from './images/chart.png';

import './ReportTemplateModal.scss';

export default function ReportTemplateModal({onClose, onConfirm, initialDefinitions}) {
  const templateGroups = [
    {
      name: 'blankGroup',
      description: null,
      templates: [{name: 'blank'}],
    },
    {
      name: 'templatesGroup',
      description: null,
      templates: [
        {
          name: 'p75Duration',
          img: durationProgress,
          disabled: (definitions) => definitions.length === 0,
          config: {
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
        {
          name: 'percentSLAMet',
          description: null,
          img: percentageProgress,
          disabled: (definitions) => definitions.length === 0,
          config: {
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
        {
          name: 'chart',
          description: null,
          img: chartImg,
          disabled: (definitions) => definitions.length === 0,
          config: {
            view: {entity: 'processInstance', properties: ['frequency']},
            groupBy: {type: 'startDate', value: {unit: 'automatic'}},
            visualization: 'bar',
            configuration: {
              xLabel: t('report.groupBy.startDate'),
              yLabel: t('report.view.pi') + ' ' + t('report.view.count'),
            },
          },
        },
        {
          name: 'heatmap',
          description: null,
          img: heatmapImg,
          disabled: (definitions) => definitions.length !== 1,
          config: {
            view: {entity: 'flowNode', properties: ['frequency']},
            groupBy: {type: 'flowNodes', value: null},
            visualization: 'heat',
            configuration: {
              xLabel: t('report.groupBy.flowNodes'),
              yLabel: t('report.view.fn') + ' ' + t('report.view.count'),
            },
          },
        },
        {
          name: 'table',
          description: null,
          img: tableImg,
          disabled: (definitions) => definitions.length === 0,
          config: {
            view: {entity: 'userTask', properties: ['frequency']},
            groupBy: {type: 'userTasks', value: null},
            visualization: 'table',
            configuration: {
              xLabel: t('report.groupBy.userTasks'),
              yLabel: t('report.view.userTask') + ' ' + t('report.view.count'),
            },
          },
        },
        {
          name: 'number',
          description: null,
          img: durationImg,
          disabled: (definitions) => definitions.length === 0,
          config: {
            view: {entity: 'processInstance', properties: ['duration']},
            groupBy: {type: 'none', value: null},
            visualization: 'number',
            configuration: {
              precision: 2,
            },
          },
        },
        {
          name: 'percentSuccess',
          description: null,
          img: percentageProgress,
          disabled: (definitions) => definitions.length !== 1,
          config: {
            view: {entity: 'processInstance', properties: ['percentage']},
            groupBy: {type: 'none', value: null},
            visualization: 'number',
            configuration: {
              targetValue: {
                active: true,
                isKpi: true,
                countProgress: {
                  baseline: '0',
                  target: '70',
                },
              },
            },
            filter: [
              {
                type: 'executedFlowNodes',
                appliedTo: ['definition'],
                filterLevel: 'instance',
                data: {
                  operator: 'in',
                  values: ['StartEvent_1'],
                },
              },
            ],
          },
        },
        {
          name: 'percentAutomated',
          description: null,
          img: percentageProgress,
          disabled: (definitions) => definitions.length !== 1,
          config: {
            view: {entity: 'processInstance', properties: ['percentage']},
            groupBy: {type: 'none', value: null},
            visualization: 'number',
            configuration: {
              targetValue: {
                active: true,
                isKpi: true,
                countProgress: {
                  baseline: '0',
                  target: '90',
                },
              },
            },
            filter: [
              {
                type: 'executedFlowNodes',
                appliedTo: ['definition'],
                filterLevel: 'instance',
                data: {
                  operator: 'not in',
                  values: ['StartEvent_1'],
                },
              },
            ],
          },
        },
        {
          name: 'percentNoIncidents',
          description: null,
          img: percentageProgress,
          disabled: (definitions) => definitions.length === 0,
          config: {
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
      ],
    },
  ];

  return (
    <TemplateModal
      className="ReportTemplateModal"
      onClose={onClose}
      templateGroups={templateGroups}
      entity="report"
      blankSlate={
        <ol>
          <li>{t('templates.blankSlate.selectProcess')}</li>
          <li>{t('templates.blankSlate.selectTemplate')}</li>
        </ol>
      }
      templateToState={({name, description, template, definitions, xml}) => ({
        name,
        description,
        data: {
          ...(template || {}),
          configuration: {...(template?.configuration || {}), xml},
          definitions: definitions[0]?.key ? definitions : [],
        },
      })}
      onConfirm={onConfirm}
      initialDefinitions={initialDefinitions}
    />
  );
}
