/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {t} from 'translation';

import TemplateModal from './TemplateModal';

import analyzeSharesAsPieChart from './images/analyzeSharesAsPieChart.png';
import analyzeOrExportRawDataFromATable from './images/analyzeOrExportRawDataFromATable.png';
import compareProcessesAndVersionsInABarChart from './images/compareProcessesAndVersionsInABarChart.png';
import correlateDurationAndCountInPieChart from './images/correlateDurationAndCountInPieChart.png';
import correlateMetricsInLineBarChart from './images/correlateMetricsInLineBarChart.png';
import listIncidentsAsTable from './images/listIncidentsAsTable.png';
import locateBottlenecsOnAHitmap from './images/locateBottlenecsOnAHitmap.png';
import localeIncidentHotspotsOnAHeatmap from './images/localeIncidentHotspotsOnAHeatmap.png';
import monitorTargetAsKpi from './images/monitorTargetAsKpi.png';
import monitorTargetAsMetric from './images/monitorTargetAsMetric.png';
import monitorTargetsOverTime from './images/monitorTargetsOverTime.png';

import './ReportTemplateModal.scss';

export default function ReportTemplateModal({onClose, onConfirm, initialDefinitions}) {
  const templateGroups = [
    {
      name: 'blankGroup',
      templates: [{name: 'blank', disableDescription: true}],
    },
    {
      name: 'templatesGroup',
      templates: [
        {
          name: 'locateBottlenecsOnAHitmap',
          img: locateBottlenecsOnAHitmap,
          disabled: (definitions) => definitions.length === 0,
          config: {
            view: {
              entity: 'flowNode',
              properties: ['duration'],
            },
            groupBy: {
              type: 'flowNodes',
              value: null,
            },
            visualization: 'heat',
          },
        },
        {
          name: 'localeIncidentHotspotsOnAHeatmap',
          img: localeIncidentHotspotsOnAHeatmap,
          disabled: (definitions) => definitions.length === 0,
          config: {
            view: {
              entity: 'incident',
              properties: ['duration'],
            },
            groupBy: {
              type: 'flowNodes',
              value: null,
            },
            visualization: 'heat',
          },
        },
        {
          name: 'monitorTargetAsKpi',
          img: monitorTargetAsKpi,
          disabled: (definitions) => definitions.length === 0,
          config: {
            configuration: {
              aggregationTypes: [
                {
                  type: 'percentile',
                  value: 75.0,
                },
              ],
              precision: 1,
              targetValue: {
                durationProgress: {
                  target: {
                    unit: 'hours',
                    value: '12',
                    isBelow: true,
                  },
                },
                active: true,
                isKpi: true,
              },
            },
            view: {
              entity: 'processInstance',
              properties: ['duration'],
            },
            groupBy: {
              type: 'none',
              value: null,
            },
            visualization: 'number',
          },
        },
        {
          name: 'monitorTargetAsMetric',
          img: monitorTargetAsMetric,
          disabled: (definitions) => definitions.length === 0,
          config: {
            configuration: {
              aggregationTypes: [
                {
                  type: 'percentile',
                  value: 75.0,
                },
              ],
              precision: 1,
              targetValue: {
                active: true,
                countProgress: {
                  baseline: '1000',
                  target: '2500',
                  isBelow: false,
                },
                isKpi: false,
              },
            },
            view: {
              entity: 'processInstance',
              properties: ['frequency'],
            },
            groupBy: {
              type: 'none',
              value: null,
            },
            visualization: 'number',
          },
        },
        {
          name: 'monitorTargetsOverTime',
          img: monitorTargetsOverTime,
          disabled: (definitions) => definitions.length === 0,
          config: {
            configuration: {
              color: '#FCCB00',
              aggregationTypes: [
                {
                  type: 'avg',
                  value: null,
                },
              ],
              targetValue: {
                active: true,
                durationChart: {
                  unit: 'days',
                  isBelow: false,
                  value: '1',
                },
                isKpi: false,
              },
              xLabel: t('report.groupBy.endDate'),
              yLabel: t('report.view.pi') + ' ' + t('report.view.duration'),
            },
            view: {
              entity: 'processInstance',
              properties: ['duration'],
            },
            groupBy: {
              type: 'endDate',
              value: {
                unit: 'month',
              },
            },
            visualization: 'bar',
          },
        },
        {
          name: 'correlateMetricsInLineBarChart',
          img: correlateMetricsInLineBarChart,
          disabled: (definitions) => definitions.length === 0,
          config: {
            configuration: {
              pointMarkers: true,
              xLabel: t('report.groupBy.endDate'),
              yLabel: t('report.view.pi') + ' ' + t('report.view.count'),
            },
            view: {
              entity: 'processInstance',
              properties: ['frequency', 'duration'],
            },
            groupBy: {
              type: 'endDate',
              value: {
                unit: 'month',
              },
            },
            visualization: 'barLine',
          },
        },
        {
          name: 'correlateDurationAndCountInPieChart',
          img: correlateDurationAndCountInPieChart,
          disabled: (definitions) => definitions.length === 0,
          config: {
            configuration: {
              showInstanceCount: true,
              sorting: {
                by: 'value',
                order: 'desc',
              },
            },
            filter: [
              {
                type: 'flowNodeStartDate',
                data: {
                  type: 'relative',
                  start: {
                    value: 1,
                    unit: 'years',
                  },
                  end: null,
                  includeUndefined: false,
                  excludeUndefined: false,
                  flowNodeIds: null,
                },
                filterLevel: 'view',
                appliedTo: ['definition'],
              },
            ],
            view: {
              entity: 'userTask',
              properties: ['frequency', 'duration'],
            },
            groupBy: {
              type: 'userTasks',
              value: null,
            },
            visualization: 'pie',
            userTaskReport: true,
          },
        },
        {
          name: 'listIncidentsAsTable',
          img: listIncidentsAsTable,
          disabled: (definitions) => definitions.length === 0,
          config: {
            configuration: {
              showInstanceCount: true,
              tableColumns: {
                includeNewVariables: true,
                excludedColumns: [],
                includedColumns: [],
                columnOrder: ['Incidents by Flow Node', 'Count', 'Relative Frequency '],
              },
            },
            view: {
              entity: 'incident',
              properties: ['frequency'],
            },
            groupBy: {
              type: 'flowNodes',
              value: null,
            },
            visualization: 'table',
          },
        },
        {
          name: 'compareProcessesAndVersionsInABarChart',
          img: compareProcessesAndVersionsInABarChart,
          disabled: (definitions) => definitions.length < 2,
          config: {
            configuration: {
              color: '#00d0a3',
              sorting: {
                by: 'value',
                order: 'desc',
              },
              xLabel: t('common.process.label'),
              yLabel: t('report.view.pi') + ' ' + t('report.view.count'),
            },
            view: {
              entity: 'processInstance',
              properties: ['frequency'],
            },
            distributedBy: {
              type: 'process',
              value: null,
            },
            groupBy: {
              type: 'none',
              value: null,
            },
            visualization: 'bar',
          },
        },
        {
          name: 'analyzeSharesAsPieChart',
          img: analyzeSharesAsPieChart,
          disabled: (definitions) => definitions.length === 0,
          config: {
            configuration: {
              alwaysShowAbsolute: true,
            },
            view: {
              entity: 'processInstance',
              properties: ['frequency'],
            },
            groupBy: {
              type: 'startDate',
              value: {
                unit: 'year',
              },
            },
            visualization: 'pie',
          },
        },
        {
          name: 'analyzeOrExportRawDataFromATable',
          img: analyzeOrExportRawDataFromATable,
          disabled: (definitions) => definitions.length === 0,
          config: {
            configuration: {
              showInstanceCount: true,
              sorting: {
                by: 'startDate',
                order: 'desc',
              },
            },
            view: {
              entity: null,
              properties: ['rawData'],
            },
            groupBy: {
              type: 'none',
              value: null,
            },
            visualization: 'table',
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
