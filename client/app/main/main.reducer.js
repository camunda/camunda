import {createRouterReducer, getRouter} from 'router';

//For now child reducer is just child, but that will change
const router = getRouter();
const routerReducer = createRouterReducer(router);

export function reducer(state = {version: 'alpha-cat-1'}, action) {
  return routerReducer(state, action);
}
