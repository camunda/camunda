import {createRouterReducer, getRouter} from 'router';
import {combineReducers} from 'redux';
import {reducer as loginReducer} from 'login';
import {reducer as loginFormReducer} from './loginForm';
import {createDynamicReducer} from 'dynamicLoader';

//For now child reducer is just child, but that will change
const router = getRouter();
const routerReducer = createRouterReducer(router);

export const reducer = combineReducers({
  login: loginReducer,
  router: routerReducer,
  loginForm: loginFormReducer,
  version: (state = 'v1.0.0') => state,
  processDisplay: createDynamicReducer('processDisplay')
});
