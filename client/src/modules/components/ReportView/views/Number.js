import React from 'react';
import ReportBlankSlate from '../ReportBlankSlate';

import ProgressBar from './ProgressBar';
import {formatters, isDurationValue} from 'services';

import './Number.scss';

export default function Number({report, data, formatter, errorMessage, targetValue, precision}) {
  if (isDurationValue(data)) {
    return <ReportBlankSlate message={errorMessage} />;
  }

  if (targetValue && targetValue.active) {
    let min, max;
    if (report.view.operation === 'count') {
      min = targetValue.countProgress.baseline;
      max = targetValue.countProgress.target;
    } else {
      min = formatters.convertDurationToSingleNumber(targetValue.durationProgress.baseline);
      max = formatters.convertDurationToSingleNumber(targetValue.durationProgress.target);
    }

    return (
      <ProgressBar min={min} max={max} value={data} formatter={formatter} precision={precision} />
    );
  }

  return <span className="Number">{formatter(data, precision)}</span>;
}
