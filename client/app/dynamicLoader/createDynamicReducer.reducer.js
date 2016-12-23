import {onModuleLoaded} from './registry.service';

export function createDynamicReducer(module) {
  let reducer = () => 'loading';

  onModuleLoaded(module)
    .then(({reducer: _reducer}) => {
      reducer = _reducer;
    });

  return (state, action) => reducer(state, action);
}
