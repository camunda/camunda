/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import './DateButton.scss';

import {Button} from 'components';
import {t} from 'translation';

export default class DateButton extends React.Component {
  render() {
    return (
      <Button onClick={this.setDate} className="DateButton">
        {t(`common.filter.dateModal.buttons.${this.props.dateLabel}`)}
      </Button>
    );
  }

  setDate = evt => {
    evt.preventDefault();

    const range = getDateRange(this.props.dateLabel);

    this.props.setDates({
      startDate: moment(range.start).format(this.props.format),
      endDate: moment(range.end).format(this.props.format)
    });
  };
}

const DAY = 86400000;

function getDateRange(type) {
  const now = Date.now();
  const today = new Date();

  switch (type) {
    case 'today':
      return {start: today, end: today};
    case 'yesterday':
      return {start: new Date(now - DAY), end: new Date(now - DAY)};
    case 'past7':
      return {start: new Date(now - 6 * DAY), end: today};
    case 'past30':
      return {start: new Date(now - 29 * DAY), end: today};
    case 'lastWeek': {
      return {
        start: new Date(now - 7 * DAY - (today.getDay() - 1) * DAY),
        end: new Date(now - today.getDay() * DAY)
      };
    }
    case 'lastMonth': {
      const start = new Date();
      const end = new Date();

      start.setDate(1);
      start.setMonth(start.getMonth() - 1);
      end.setDate(0);
      return {start, end};
    }
    case 'lastYear': {
      const start = new Date();
      const end = new Date();

      start.setMonth(0);
      start.setDate(1);
      start.setFullYear(start.getFullYear() - 1);
      end.setMonth(0);
      end.setDate(0);
      return {start, end};
    }
    case 'thisWeek': {
      return {
        start: new Date(now - (today.getDay() - 1) * DAY),
        end: new Date(now - today.getDay() * DAY + 7 * DAY)
      };
    }
    case 'thisMonth': {
      const start = new Date();
      const end = new Date();

      start.setDate(1);
      end.setDate(1);
      end.setMonth(end.getMonth() + 1);
      end.setDate(0);
      return {start, end};
    }
    case 'thisYear': {
      const start = new Date();
      const end = new Date();

      start.setMonth(0);
      start.setDate(1);
      end.setFullYear(end.getFullYear() + 1);
      end.setMonth(0);
      end.setDate(0);
      return {start, end};
    }
    default:
      return null;
  }
}
