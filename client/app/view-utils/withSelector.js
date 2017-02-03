import {Scope} from './scope';
import {jsx} from './jsx';

export function withSelector(Component) {
  return ({selector, ...rest}) => {
    return <Scope selector={selector}>
      <Component {...rest} />
    </Scope>;
  };
}
