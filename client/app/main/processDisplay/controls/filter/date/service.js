import {createCreateStartDateFilterAction} from './routeReducer';
import {dispatch} from '../store';
import moment from 'moment';

export const FORMAT = 'YYYY-MM-DD';

export function createStartDateFilter(startDate, endDate) {
  dispatch(createCreateStartDateFilterAction(startDate, endDate));
}

export function formatDate(dateObj, {endOfDay, withTime} = {}) {
  let date = moment(dateObj);

  if (endOfDay) {
    date = date.endOf('day');
  } else {
    date = date.startOf('day');
  }

  if (withTime) {
    return date.format(FORMAT + 'THH:mm:ss');
  } else {
    return date.format(FORMAT);
  }
}
