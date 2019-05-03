/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import ProgressBar from './ProgressBar';
import {formatters} from 'services';

import './Number.scss';

export default function Number({report, formatter}) {
  const {data, result} = report;
  const {targetValue, precision} = data.configuration;

  if (targetValue && targetValue.active) {
    let min, max;
    if (data.view.property === 'frequency') {
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

  return <span className="Number">{formatter(result.data, precision)}</span>;
}
