import {getLastRoute} from 'router';
import {createDeleteFilterAction} from './routeReducer';
import {dispatch, parse} from './store';

export function deleteFilter(filter) {
  dispatch(createDeleteFilterAction(filter));
}

export function getFilter() {
  const {params} = getLastRoute();

  return parse(params);
}
