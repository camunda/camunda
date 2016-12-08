import './styles.scss';
import {Main, reducer} from './main';
import {runUpdate, createEventsBus, pipe, jsx, ACTION_EVENT_NAME} from './view-utils';

const template = <Main />;
const eventsBus = createEventsBus();
const updateComponent = runUpdate.bind(
  null,
  template(document.body, eventsBus)
);

// Very simplistic redux store implementation
function createStore(reducer, callback) {
  let state = reducer(undefined, {
    type: '@@INIT'
  });

  return {
    dispatch: (action) => {
      state = reducer(state, action);
      callback(state);
    },
    getState: () => state
  };
}

const store = createStore(reducer, () => {
  setTimeout(
    pipe(
      store.getState(),
      updateComponent
    ),
    0
  );
});

updateComponent(store.getState()); // First update

document.addEventListener(ACTION_EVENT_NAME, ({reduxAction}) => {
  store.dispatch(reduxAction);
});
