import {createCreateStartDateFilterAction} from './routeReducer';
import {dispatch} from '../store';

export function createStartDateFilter(startDate, endDate) {
  dispatch(createCreateStartDateFilterAction(startDate, endDate));
}

export function formatDate(dateObj) {
  return dateObj.toISOString().substr(0, 10);
}
