import {parseFilterForRequest} from 'modules/utils/filter';
import {FILTER_SELECTION} from 'modules/constants';

// keys for values that fallback to the localState
export const localStateKeys = [
  'filter',
  'filterCount',
  'selectionCount',
  'instancesInSelectionsCount'
];

// keys for values that fallback to the api
export const apiKeys = ['runningInstancesCount', 'incidentsCount'];

export const filtersMap = {
  incidentsCount: parseFilterForRequest(FILTER_SELECTION.incidents),
  runningInstancesCount: parseFilterForRequest(FILTER_SELECTION.running)
};
