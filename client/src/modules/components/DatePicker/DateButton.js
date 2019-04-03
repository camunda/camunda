/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import moment from 'moment';

import './DateButton.scss';

import {Button} from 'components';

export default class DateButton extends React.Component {
  render() {
    return (
      <Button onClick={this.setDate} className="DateButton">
        {this.props.dateLabel}
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
    case DateButton.TODAY:
      return {start: today, end: today};
    case DateButton.YESTERDAY:
      return {start: new Date(now - DAY), end: new Date(now - DAY)};
    case DateButton.PAST7:
      return {start: new Date(now - 6 * DAY), end: today};
    case DateButton.PAST30:
      return {start: new Date(now - 29 * DAY), end: today};
    case DateButton.LAST_WEEK: {
      return {
        start: new Date(now - 7 * DAY - (today.getDay() - 1) * DAY),
        end: new Date(now - today.getDay() * DAY)
      };
    }
    case DateButton.LAST_MONTH: {
      const start = new Date();
      const end = new Date();

      start.setDate(1);
      start.setMonth(start.getMonth() - 1);
      end.setDate(0);
      return {start, end};
    }
    case DateButton.LAST_YEAR: {
      const start = new Date();
      const end = new Date();

      start.setMonth(0);
      start.setDate(1);
      start.setFullYear(start.getFullYear() - 1);
      end.setMonth(0);
      end.setDate(0);
      return {start, end};
    }
    case DateButton.THIS_WEEK: {
      return {
        start: new Date(now - (today.getDay() - 1) * DAY),
        end: new Date(now - today.getDay() * DAY + 7 * DAY)
      };
    }
    case DateButton.THIS_MONTH: {
      const start = new Date();
      const end = new Date();

      start.setDate(1);
      end.setDate(1);
      end.setMonth(end.getMonth() + 1);
      end.setDate(0);
      return {start, end};
    }
    case DateButton.THIS_YEAR: {
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

DateButton.TODAY = 'Today';
DateButton.YESTERDAY = 'Yesterday';
DateButton.PAST7 = 'Past 7 days';
DateButton.PAST30 = 'Past 30 days';
DateButton.LAST_WEEK = 'Last Week';
DateButton.LAST_MONTH = 'Last Month';
DateButton.LAST_YEAR = 'Last Year';
DateButton.THIS_WEEK = 'This Week';
DateButton.THIS_MONTH = 'This Month';
DateButton.THIS_YEAR = 'This Year';
