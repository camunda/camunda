import {getLastRoute} from 'router';
import {
  createDeleteFilterAction, createCreateStartDateFilterAction
} from './reducer';
import {dispatch, parse} from './store';

export function deleteFilter(filter) {
  dispatch(createDeleteFilterAction(filter));
}

export function createStartDateFilter(startDate, endDate) {
  dispatch(createCreateStartDateFilterAction(startDate, endDate));
}

export function formatDate(dateObj) {
  return dateObj.toISOString().substr(0, 10);
}

export function getFilter() {
  const {params} = getLastRoute();

  return parse(params);
}
