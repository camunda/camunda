import {addChildren} from './jsx';

export function Children({children}) {
  return (node, eventsBus) => {
    return addChildren(node, eventsBus, children);
  };
}
