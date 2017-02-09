import {withChildren} from './withChildren';

export function createStateInjector() {
  let state;

  const StateInjector = withChildren(() => {
    return () => {
      return _state => state = _state;
    };
  });

  StateInjector.getState = () => state;

  return StateInjector;
}
