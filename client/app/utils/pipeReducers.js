export function pipeReducers(...reducers) {
  return (state, action) => {
    return reducers.reduce((state, reducer) => {
      return reducer(state, action);
    }, state);
  };
}
