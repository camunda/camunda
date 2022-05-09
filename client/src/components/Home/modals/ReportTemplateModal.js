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
import tableImg from './images/table.png';
import chartImg from './images/chart.png';

import './ReportTemplateModal.scss';

export default function ReportTemplateModal({onClose}) {
  const templateGroups = [
    {
      name: 'blankGroup',
      templates: [{name: 'blank'}],
    },
    {
      name: 'templatesGroup',
      templates: [
        {
          name: 'chart',
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
          name: 'number',
          img: durationImg,
          disabled: (definitions) => definitions.length === 0,
          config: {
            view: {entity: 'processInstance', properties: ['duration']},
            groupBy: {type: 'none', value: null},
            visualization: 'number',
            configuration: {
              precision: 3,
            },
          },
        },
        {
          name: 'table',
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
      templateToState={({name, template, definitions, xml}) => ({
        name,
        data: {
          ...(template || {}),
          configuration: {...(template?.configuration || {}), xml},
          definitions: definitions[0]?.key ? definitions : [],
        },
      })}
    />
  );
}
