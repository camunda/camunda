import {dispatchAction} from 'view-utils';
import {createOpenDateFilterModalAction,
        createCloseDateFilterModalAction,
        createDeleteFilterAction,
        createCreateStartDateFilterAction} from './reducer';

export function openModal() {
  dispatchAction(createOpenDateFilterModalAction());
}

export function closeModal() {
  dispatchAction(createCloseDateFilterModalAction());
}

export function deleteFilter(filter) {
  dispatchAction(createDeleteFilterAction(filter));
}

export function createStartDateFilter(startDate, endDate) {
  dispatchAction(createCreateStartDateFilterAction(startDate, endDate));
}

export function formatDate(dateObj) {
  return dateObj.toISOString().substr(0, 10);
}
