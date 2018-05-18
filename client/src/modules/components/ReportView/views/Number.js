import React from 'react';
import ReportBlankSlate from '../ReportBlankSlate';

import ProgressBar from './ProgressBar';
import {formatters} from 'services';

import './Number.css';

export default function Number({data, formatter, errorMessage, targetValue}) {
  if (typeof data !== 'number') {
    return <ReportBlankSlate message={errorMessage} />;
  }

  if (targetValue && targetValue.active) {
    let {baseline: min, target: max} = targetValue.values;
    if (typeof min === 'object') {
      min = formatters.convertDurationToSingleNumber(min);
      max = formatters.convertDurationToSingleNumber(max);
    }
    return <ProgressBar min={min} max={max} value={data} formatter={formatter} />;
  }

  return <span className="Number">{formatter(data)}</span>;
}
