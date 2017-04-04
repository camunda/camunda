import {pipe, ACTION_EVENT_NAME} from 'view-utils';
import {createStore} from 'redux';
import deepCopy from 'deep-copy';

export function initStore(updateComponent, reducer) {
  const store = createStore(reducer, window.__REDUX_DEVTOOLS_EXTENSION__ && window.__REDUX_DEVTOOLS_EXTENSION__());

  store.subscribe(() => {
    setTimeout(
      pipe(
        store.getState,
        deepCopy,
        updateComponent
      ),
      0
    );
  });

  updateComponent(store.getState()); // First update

  document.addEventListener(ACTION_EVENT_NAME, ({reduxAction}) => {
    store.dispatch(reduxAction);
  });
}
