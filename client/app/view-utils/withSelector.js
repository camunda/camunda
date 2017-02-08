import {Scope} from './Scope';
import {jsx} from './jsx';

export function withSelector(Component) {
  return ({selector, ...rest}) => {
    return <Scope selector={selector}>
      <Component {...rest} />
    </Scope>;
  };
}
