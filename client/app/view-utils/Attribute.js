import {withSelector} from './withSelector';

export const isTruthy = x => x;
export const isFalsy = x => !x;

export const Attribute = withSelector(({attribute, predicate = isTruthy}) => {
  return (node) => {
    return (state) => {
      if (predicate(state)) {
        node.setAttribute(attribute, state.toString());
      } else {
        node.removeAttribute(attribute);
      }
    };
  };
});
