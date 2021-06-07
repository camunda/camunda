/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {t} from 'translation';

import TemplateModal from './TemplateModal';

import heatmapImg from './images/heatmap.jpg';
import durationImg from './images/duration.svg';
import tableImg from './images/table.svg';
import chartImg from './images/chart.svg';

import './ReportTemplateModal.scss';

export default function ReportTemplateModal({onClose}) {
  const templates = [
    {name: 'blank'},
    {
      name: 'heatmap',
      img: heatmapImg,
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
      config: {
        view: {entity: 'processInstance', properties: ['duration']},
        groupBy: {type: 'none', value: null},
        visualization: 'number',
        configuration: {
          yLabel: t('report.view.pi') + ' ' + t('report.view.duration'),
        },
      },
    },
    {
      name: 'table',
      img: tableImg,
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
      name: 'chart',
      img: chartImg,
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
  ];

  return (
    <TemplateModal
      className="ReportTemplateModal"
      onClose={onClose}
      templates={templates}
      entity="report"
      templateToState={({name, template, definitions, xml}) => ({
        name,
        data: {
          ...(template || {}),
          configuration: {...(template?.configuration || {}), xml},
          definitions,
        },
      })}
    />
  );
}
