import {Scope} from './Scope';
import {jsx} from './jsx';

export function withSelector(Component, property = 'selector') {
  return ({[property]: selector, ...rest}) => {
    return <Scope selector={selector}>
      <Component {...rest} />
    </Scope>;
  };
}
