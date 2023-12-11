/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {t} from 'translation';

import TemplateModal from './TemplateModal';
import {
  analyzeOrExportRawDataFromATableTemplate,
  analyzeSharesAsPieChartTemplate,
  compareProcessesAndVersionsInABarChartTemplate,
  correlateDurationAndCountInPieChartTemplate,
  correlateMetricsInLineBarChartTemplate,
  listIncidentsAsTableTemplate,
  localeIncidentHotspotsOnAHeatmapTemplate,
  locateBottlenecsOnAHitmapTemplate,
  monitorTargetAsKpiTemplate,
  monitorTargetAsMetricTemplate,
  monitorTargetsOverTimeTemplate,
} from './templates/report';

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
        locateBottlenecsOnAHitmapTemplate(),
        localeIncidentHotspotsOnAHeatmapTemplate(),
        monitorTargetAsKpiTemplate(),
        monitorTargetAsMetricTemplate(),
        monitorTargetsOverTimeTemplate(),
        correlateMetricsInLineBarChartTemplate(),
        correlateDurationAndCountInPieChartTemplate(),
        listIncidentsAsTableTemplate(),
        compareProcessesAndVersionsInABarChartTemplate(),
        analyzeSharesAsPieChartTemplate(),
        analyzeOrExportRawDataFromATableTemplate(),
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
