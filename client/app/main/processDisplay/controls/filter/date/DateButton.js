import {jsx, OnEvent} from 'view-utils';
import {formatDate} from './service';
import $ from 'jquery';

export const TODAY = 'Today';
export const YESTERDAY = 'Yesterday';
export const PAST7 = 'Past 7 days';
export const PAST30 = 'Past 30 days';
export const LAST_WEEK = 'Last Week';
export const LAST_MONTH = 'Last Month';
export const LAST_YEAR = 'Last Year';
export const THIS_WEEK = 'This Week';
export const THIS_MONTH = 'This Month';
export const THIS_YEAR = 'This Year';

export function DateButton({dateLabel}) {
  const range = getDateRange(dateLabel);

  return <button type="button" className="btn btn-default">
    <OnEvent event="click" listener={setDate} />
    {dateLabel}
  </button>;

  function setDate({state: {start, end}}) {
    $(start).datepicker('setDate', formatDate(range.start));
    $(end).datepicker('setDate', formatDate(range.end));
  }
}

const DAY = 86400000;

function getDateRange(type) {
  const now = Date.now();
  const today = new Date();

  switch (type) {
    case TODAY: return {start: today, end: today};
    case YESTERDAY: return {start: new Date(now - DAY), end: new Date(now - DAY)};
    case PAST7: return {start: new Date(now - 6 * DAY), end: today};
    case PAST30: return {start: new Date(now - 29 * DAY), end: today};
    case LAST_WEEK: {
      return {
        start: new Date(now - 7 * DAY - (today.getDay() - 1) * DAY),
        end: new Date(now - (today.getDay()) * DAY)
      };
    }
    case LAST_MONTH: {
      const start = new Date();
      const end = new Date();

      start.setDate(1);
      start.setMonth(start.getMonth() - 1);
      end.setDate(0);
      return {start, end};
    }
    case LAST_YEAR: {
      const start = new Date();
      const end = new Date();

      start.setMonth(0);
      start.setDate(1);
      start.setFullYear(start.getFullYear() - 1);
      end.setMonth(0);
      end.setDate(0);
      return {start, end};
    }
    case THIS_WEEK: {
      return {
        start: new Date(now - (today.getDay() - 1) * DAY),
        end: new Date(now - (today.getDay()) * DAY + 7 * DAY)
      };
    }
    case THIS_MONTH: {
      const start = new Date();
      const end = new Date();

      start.setDate(1);
      end.setMonth(end.getMonth() + 1);
      end.setDate(0);
      return {start, end};
    }
    case THIS_YEAR: {
      const start = new Date();
      const end = new Date();

      start.setMonth(0);
      start.setDate(1);
      end.setFullYear(end.getFullYear() + 1);
      end.setMonth(0);
      end.setDate(0);
      return {start, end};
    }
  }
}
