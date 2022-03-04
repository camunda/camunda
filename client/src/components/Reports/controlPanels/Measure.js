/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {Select, SelectionPreview, Button} from 'components';
import {t} from 'translation';
import {reportConfig, createReportUpdate} from 'services';

import AggregationType from './AggregationType';

import './Measure.scss';

export default function Measure({report, onChange}) {
  const selectedView = reportConfig.process.view.find(({matcher}) => matcher(report));

  function updateMeasure(newMeasures) {
    onChange(
      createReportUpdate('process', report, 'view', selectedView.key, {
        view: {properties: {$set: newMeasures}},
      })
    );
  }

  if (report.view.properties?.length === 2) {
    return (
      <>
        <li className="Measure select">
          <span className="label">{t('report.measure')}</span>
          <SelectionPreview onClick={() => updateMeasure(['duration'])}>
            <span>{t('report.view.count')}</span>
          </SelectionPreview>
        </li>
        <li className="Measure select">
          <span className="label"></span>
          <SelectionPreview onClick={() => updateMeasure(['frequency'])}>
            <span>
              {report.view.entity === 'incident'
                ? t('report.view.resolutionDuration')
                : t('report.view.duration')}
            </span>
          </SelectionPreview>
          <AggregationType report={report} onChange={onChange} />
        </li>
      </>
    );
  } else {
    return (
      <>
        <li className="Measure select">
          <span className="label">{t('report.measure')}</span>
          <Select
            value={report.view.properties[0]}
            onChange={(property) => updateMeasure([property])}
          >
            <Select.Option value="frequency">{t('report.view.count')}</Select.Option>
            <Select.Option value="duration">
              {report.view.entity === 'incident'
                ? t('report.view.resolutionDuration')
                : t('report.view.duration')}
            </Select.Option>
          </Select>
          <AggregationType report={report} onChange={onChange} />
        </li>
        <li className="addMeasure">
          <Button onClick={() => updateMeasure(['frequency', 'duration'])}>
            + {t('report.addMeasure')}
          </Button>
        </li>
      </>
    );
  }
}
