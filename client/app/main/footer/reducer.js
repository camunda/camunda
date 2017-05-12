import {combineReducers} from 'redux';
import {reducer as progress} from './progress';

export const reducer = combineReducers({
  version: (state = 'v1.0.0') => state,
  progress
});
