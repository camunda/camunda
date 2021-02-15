/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useRef} from 'react';
import fitty from 'fitty';

import {formatters, reportConfig} from 'services';
import {t} from 'translation';

import ProgressBar from './ProgressBar';

import './Number.scss';

export default function Number({report, formatter}) {
  const {data, result, reportType} = report;
  const {targetValue, precision} = data.configuration;
  const numberText = useRef();

  useEffect(() => {
    if (numberText.current) {
      fitty(numberText.current, {
        minSize: 5,
        maxSize: 55,
      });
    }
  }, [targetValue]);

  if (targetValue && targetValue.active) {
    let min, max;
    if (data.view.property === 'frequency' || data.view.entity === 'variable') {
      min = targetValue.countProgress.baseline;
      max = targetValue.countProgress.target;
    } else {
      min = formatters.convertDurationToSingleNumber(targetValue.durationProgress.baseline);
      max = formatters.convertDurationToSingleNumber(targetValue.durationProgress.target);
    }

    return (
      <ProgressBar
        min={min}
        max={max}
        value={result.data}
        formatter={formatter}
        precision={precision}
      />
    );
  }

  let viewString;

  if (data.view.entity === 'variable') {
    viewString = data.view.property.name;
  } else {
    const config = reportConfig[reportType];
    const selectedView = config.findSelectedOption(config.options.view, 'data', data.view);
    viewString = selectedView.key
      .split('_')
      .map((key) => t('report.view.' + key))
      .join(' ');
  }

  if (data.view.property === 'duration' || data.view.entity === 'variable') {
    viewString += ' - ' + t('report.config.aggregationShort.' + data.configuration.aggregationType);
  }

  return (
    <div className="Number">
      <div className="container" ref={numberText}>
        <div className="data">{formatter(result.data, precision)}</div>
        <div className="label">{viewString}</div>
      </div>
    </div>
  );
}
