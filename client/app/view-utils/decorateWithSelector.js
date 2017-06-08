import {jsx} from './jsx';
import {Scope} from './Scope';

export function decorateWithSelector(Component, selector) {
  return attributes => {
    return <Scope selector={selector}>
      <Component {...attributes} />
    </Scope>;
  };
}
