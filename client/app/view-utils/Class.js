import {withSelector} from './withSelector';
import {hasClass, addClass, removeClass} from './classFunctions';
import {isTruthy} from './Attribute';

export const Class = withSelector(({className, predicate = isTruthy}) => {
  return (node) => {
    return (state) => {
      const shouldAdd = predicate(state);
      const nodeHasClass = hasClass(node, className);

      if (shouldAdd && !nodeHasClass) {
        addClass(node, className);
      } else if (!shouldAdd && nodeHasClass) {
        removeClass(node, className);
      }
    };
  };
});
