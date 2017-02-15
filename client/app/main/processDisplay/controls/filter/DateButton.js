import {jsx, OnEvent} from 'view-utils';
import {formatDate} from './service';

export function DateButton({date}) {
  const range = getDateRange(date);

  return <button type="button" className="btn btn-default">
    <OnEvent event='click' listener={setDate} />
    {date}
  </button>;

  function setDate({state: {start, end}}) {
    start.value = formatDate(range.start);
    end.value = formatDate(range.end);
  }
}

const DAY = 86400000;

function getDateRange(type) {
  const now = Date.now();
  const today = new Date();

  switch (type) {
    case 'Today': return {start: today, end: today};
    case 'Yesterday': return {start: new Date(now - DAY), end: new Date(now - DAY)};
    case 'Past 7 days': return {start: new Date(now - 6 * DAY), end: today};
    case 'Past 30 days': return {start: new Date(now - 29 * DAY), end: today};
    case 'Last Week': {
      return {start: new Date(now - 7 * DAY - (today.getDay() - 1) * DAY),
        end: new Date(now - (today.getDay()) * DAY)};
    }
    case 'Last Month': {
      const start = new Date();
      const end = new Date();

      start.setMonth(start.getMonth() - 1);
      start.setDate(1);
      end.setDate(0);
      return {start, end};
    }
    case 'Last Year': {
      const start = new Date();
      const end = new Date();

      start.setMonth(0);
      start.setDate(1);
      start.setFullYear(start.getFullYear() - 1);
      end.setMonth(0);
      end.setDate(0);
      return {start, end};
    }
    case 'This Week': {
      return {start: new Date(now - (today.getDay() - 1) * DAY),
        end: new Date(now - (today.getDay()) * DAY + 7 * DAY)};
    }
    case 'This Month': {
      const start = new Date();
      const end = new Date();

      start.setDate(1);
      end.setMonth(end.getMonth() + 1);
      end.setDate(0);
      return {start, end};
    }
    case 'This Year': {
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
