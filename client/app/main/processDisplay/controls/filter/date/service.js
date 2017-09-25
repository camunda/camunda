import {createCreateStartDateFilterAction} from './routeReducer';
import {dispatch} from '../store';
import moment from 'moment';

export const FORMAT = 'YYYY-MM-DD';

export function createStartDateFilter(startDate, endDate) {
  dispatch(createCreateStartDateFilterAction(startDate, endDate));
}

export function formatDate(dateObj) {
  return moment(dateObj).format(FORMAT);
}

export function sortDates({start, end}) {
  if (start.isBefore(end)) {
    return {start, end};
  }

  return {
    start: end,
    end: start
  };
}
