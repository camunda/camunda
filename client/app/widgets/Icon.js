import {jsx, Children} from 'view-utils';

export function Icon({icon, children}) {
  return <span className={'glyphicon ' + icon}>
    <Children children={children} />
  </span>;
}
