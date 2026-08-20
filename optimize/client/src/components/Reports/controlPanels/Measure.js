/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';

import {Select, SelectionPreview} from 'components';
import {t} from 'translation';
import {reportConfig, createReportUpdate} from 'services';

import AggregationType from './AggregationType';

import './Measure.scss';

const measureOrder = ['frequency', 'percentage', 'duration'];

export default function Measure({report, onChange}) {
  const selectedView = reportConfig.view.find(({matcher}) => matcher(report));
  const firstMeasure = report.view.properties?.[0];
  const secondMeasure = report.view.properties?.[1];
  const isIncidentReport = report.view.entity === 'incident';

  function updateMeasure(newMeasures) {
    onChange(
      createReportUpdate(report, 'view', selectedView.key, {
        view: {properties: {$set: newMeasures}},
      })
    );
  }

  const options = (
    <>
      <Select.Option
        label={getLabel('frequency', isIncidentReport)}
        value="frequency"
        disabled={firstMeasure === 'frequency'}
      />
      {report.view.entity === 'processInstance' && (
        <Select.Option
          label={getLabel('percentage', isIncidentReport)}
          value="percentage"
          disabled={firstMeasure === 'percentage'}
        />
      )}
      <Select.Option
        label={getLabel('duration', isIncidentReport)}
        value="duration"
        disabled={firstMeasure === 'duration'}
      />
    </>
  );

  if (report.view.properties?.length === 2) {
    return (
      <>
        <li className="Measure select">
          <span className="label">{t('report.measure')}</span>
          <SelectionPreview onClick={() => updateMeasure([secondMeasure])}>
            <span>{getLabel(firstMeasure, isIncidentReport)}</span>
          </SelectionPreview>
        </li>
        <li className="Measure select">
          <span className="label"></span>
          <SelectionPreview onClick={() => updateMeasure([firstMeasure])}>
            <span>
              <span>{getLabel(secondMeasure, isIncidentReport)}</span>
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
          <Select value={firstMeasure} onChange={(property) => updateMeasure([property])}>
            {options}
          </Select>
          <AggregationType report={report} onChange={onChange} />
        </li>
        <li className="addMeasure">
          <Select
            onChange={(secondMeasure) => {
              updateMeasure(
                [firstMeasure, secondMeasure].sort(
                  (a, b) => measureOrder.indexOf(a) - measureOrder.indexOf(b)
                )
              );
            }}
            placeholder={'+ ' + t('report.addMeasure')}
          >
            {options}
          </Select>
        </li>
      </>
    );
  }
}

function getLabel(measure, isIncident) {
  if (measure === 'frequency') {
    return t('report.view.count');
  }

  if (measure === 'percentage') {
    return t('report.view.percentage');
  }

  if (isIncident) {
    return t('report.view.resolutionDuration');
  }

  return t('report.view.duration');
}
