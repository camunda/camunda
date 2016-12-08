import isEqual from 'lodash.isequal';

export function updateOnlyWhenStateChanges(update, equal = isEqual) {
  let lastState;

  function updateWrapper(state) {
    if (!equal(lastState, state)) {
      lastState = state;

      update(state);
    }
  }

  updateWrapper.getLastState = () => lastState;

  return updateWrapper;
}
