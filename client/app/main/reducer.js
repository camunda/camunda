import {createRouterReducer, getRouter} from 'router';
import {combineReducers} from 'redux';
import {reducer as login} from 'login';
import {reducer as loginForm} from './loginForm';
import {reducer as notifications} from './notifications';
import {createDynamicReducer} from 'dynamicLoader';

//For now child reducer is just child, but that will change
const router = getRouter();
const routerReducer = createRouterReducer(router);

export const reducer = combineReducers({
  login,
  router: routerReducer,
  loginForm,
  notifications,
  version: (state = 'v1.0.0') => state,
  processDisplay: createDynamicReducer('processDisplay')
});
