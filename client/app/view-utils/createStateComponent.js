import {withChildren} from './withChildren';

export function createStateComponent() {
  let state;

  const State = withChildren(() => {
    return () => {
      return _state => state = _state;
    };
  });

  State.getState = () => state;

  return State;
}
