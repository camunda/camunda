import {withSelector} from './withSelector';

const alwaysTrue = () => true;

export const Attribute = withSelector(({attribute, predicate = alwaysTrue}) => {
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

export const isTruthy = x => x;
