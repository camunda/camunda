/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Calendar, DateRange as ReactDateRange} from 'react-date-range';
import {isValid, isAfter, isEqual} from 'date-fns';

import {getLanguage} from 'translation';
import {globalLocale} from 'dates';

import 'react-date-range/dist/styles.css'; // main style file
import 'react-date-range/dist/theme/default.css'; // theme css file
import './DateRange.scss';

export default function DateRange({type, startDate, endDate, onDateChange, endDateSelected}) {
  const validStartDate = isValid(startDate) ? startDate : new Date();
  const validEndDate = isValid(endDate) ? endDate : new Date();

  let range;
  if (
    isValid(startDate) &&
    isValid(endDate) &&
    (isEqual(startDate, endDate) || isAfter(endDate, startDate))
  ) {
    range = {
      startDate,
      endDate,
    };
  } else {
    range = {
      startDate: new Date(),
      endDate: new Date(),
    };
  }

  return type === 'between' ? (
    <ReactDateRange
      focusedRange={endDateSelected ? [0, 1] : [0, 0]}
      locale={getLanguage() !== 'en' ? globalLocale : undefined}
      ranges={[range]}
      months={2}
      onChange={({range1}) => onDateChange(range1)}
      direction="horizontal"
    />
  ) : (
    <Calendar
      date={type === 'after' ? validStartDate : validEndDate}
      locale={getLanguage() !== 'en' ? globalLocale : undefined}
      onChange={(date) => {
        if (type === 'after') {
          onDateChange({startDate: date, endDate: null});
        } else {
          onDateChange({startDate: null, endDate: date});
        }
      }}
    />
  );
}
