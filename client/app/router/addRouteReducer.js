import {getLastRoute} from './Router';
import {getRouter} from './service';

const router = getRouter();

export function addRouteReducer({parse, format, reducer}) {
  return (action) => {
    const {name, params} = getLastRoute();
    const state = parse(params);
    const newState = reducer(state, action);

    router.goTo(name, format(params, newState));

    return newState;
  };
}
