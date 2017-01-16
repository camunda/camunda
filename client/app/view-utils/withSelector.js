import {Select} from './select';
import {jsx} from './jsx';

export function withSelector(Component) {
  return ({selector, ...rest}) => {
    return <Select selector={selector}>
      <Component {...rest} />
    </Select>;
  };
}
