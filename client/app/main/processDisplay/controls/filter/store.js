import jsurl from 'jsurl';
import {addRouteReducer, getRouter} from 'router';
import {reducer} from './routeReducer';
import {createChangeObserver} from 'utils';

const router = getRouter();
const observer = createChangeObserver({
  getter: ({params}) => parse(params)
});

const dispatchToReducer = addRouteReducer({parse, format, reducer});

export function dispatch(action) {
  const newState = dispatchToReducer(action);

  observer.setLast(newState);
}

export function parse({filter: filterStr}) {
  return jsurl.parse(filterStr);
}

export function format(params, state) {
  return {
    ...params,
    filter: jsurl.stringify(state)
  };
}

export function onHistoryStateChange(listener) {
  return router.addHistoryListener(
    observer.observeChanges(listener)
  );
}
