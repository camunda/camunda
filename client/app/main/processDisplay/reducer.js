import {reducer as views} from './views';
import {reducer as controls} from './controls';
import {combineReducers} from 'redux';

export const reducer = combineReducers({
  views,
  controls
});
