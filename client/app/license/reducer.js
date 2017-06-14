import {combineReducers} from 'redux';
import {reducer as footer} from 'main/footer';
import {reducer as notifications} from 'notifications';

export const reducer = combineReducers({
  footer,
  notifications,
});
