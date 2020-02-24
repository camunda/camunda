/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {DateRange as ReactDateRange} from 'react-date-range';
import {getLanguage} from 'translation';
import {de} from 'react-date-range/dist/locale';

import 'react-date-range/dist/styles.css'; // main style file
import 'react-date-range/dist/theme/default.css'; // theme css file
import './DateRange.scss';

export default function DateRange({startDate, endDate, onDateChange, endDateSelected}) {
  let range;
  if (startDate.isValid() && endDate.isValid() && endDate.isSameOrAfter(startDate)) {
    range = {
      startDate: startDate.toDate(),
      endDate: endDate.toDate()
    };
  } else {
    range = {
      startDate: new Date(),
      endDate: new Date()
    };
  }

  return (
    <ReactDateRange
      focusedRange={endDateSelected ? [0, 1] : [0, 0]}
      locale={getLanguage() === 'de' ? de : undefined}
      ranges={[range]}
      months={2}
      onChange={({range1}) => onDateChange(range1)}
      direction="horizontal"
    />
  );
}
