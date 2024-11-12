/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
    leftLabel = formatter(min, precision);
    rightLabel = goalExceeded
      ? formatter(value, precision)
      : `${t('report.progressBar.goal')} ` + formatter(max, precision);
    warning = isBelow ? value > max : value < max;
  }

  const getTargetStyle = () => {
    if (goalPercentage > 50) {
      return {
        right: `${100 - goalPercentage}%`,
        textAlign: 'right',
      };
    }
    return {
      left: `${goalPercentage}%`,
    };
  };

  return (
    <div className="ProgressBar">
      {goalExceeded && (
        <span className={classnames('goalLabel')} style={getTargetStyle()}>
          {t('report.progressBar.goal')} {isBelow ? '<' : '>'} {formatter(max)}
        </span>
      )}
      <div className="barContainer">
        {goalExceeded && (
          <div
            className={classnames('goalOverlay', {warning})}
            style={{
              width: `${goalPercentage}%`,
            }}
          ></div>
        )}
        <div className="progressLabel">{formatter(value, precision)}</div>
        <div
          className={classnames('filledOverlay', {goalExceeded, warning})}
          style={{width: `${relative * 100}%`}}
        />
        <div className={classnames('rangeLabels')}>
          <span>{leftLabel}</span>
          <span className="rightLabel" title={rightLabel}>
            {rightLabel}
          </span>
        </div>
      </div>
    </div>
  );
}
