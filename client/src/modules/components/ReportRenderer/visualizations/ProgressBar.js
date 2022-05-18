/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import classnames from 'classnames';

import {numberParser} from 'services';
import {t} from 'translation';

import './ProgressBar.scss';

const {isNonNegativeNumber} = numberParser;

export default function ProgressBar({min, max, value, isBelow, formatter, precision}) {
  const isInvalid = !isNonNegativeNumber(min) || !isNonNegativeNumber(max) || +max < +min;

  let relative, goalPercentage, goalExceeded, rightLabel, leftLabel, warning;

  if (isInvalid) {
    relative = 0;
    goalPercentage = 0;
    goalExceeded = false;
    leftLabel = rightLabel = t('report.progressBar.invalid');
  } else {
    relative = Math.min(1, Math.max(0, (value - min) / (max - min)));
    goalPercentage = ((max - min) * 100) / (value - min);
    goalExceeded = max < value;
    leftLabel = formatter(min);
    rightLabel = goalExceeded
      ? formatter(value, precision)
      : `${t('report.progressBar.goal')} ` + formatter(max);
    warning = isBelow ? value > max : value < max;
  }

  return (
    <div className="ProgressBar">
      <div className="barContainer">
        {goalExceeded && (
          <div
            className={classnames('goalOverlay', {warning})}
            style={{
              width: `${goalPercentage}%`,
            }}
          >
            <span className={classnames('goalLabel', {rightSide: goalPercentage > 50})}>
              {t('report.progressBar.goal')} {isBelow ? '<' : '>'} {formatter(max)}
            </span>
          </div>
        )}
        <div className="progressLabel">{formatter(value, precision)}</div>
        <div
          className={classnames('filledOverlay', {goalExceeded, warning})}
          style={{width: `${relative * 100}%`}}
        />
        <div className={classnames('rangeLabels')}>
          {leftLabel}
          <span className="rightLabel">{rightLabel}</span>
        </div>
      </div>
    </div>
  );
}
