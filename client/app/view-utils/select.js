import {pipe} from './pipe';
import {get} from './get';
import {addChildren} from './jsx';
import {runUpdate} from './runUpdate';

export function Select({selector, children}) {
  if (typeof selector === 'string') {
    return JsxConnect({
      selector: get.bind(null, selector),
      children
    });
  }

  return (node, eventsBus) => {
    const updateFunctions = addChildren(node, eventsBus, children);

    return pipe(
      selector,
      runUpdate.bind(null, updateFunctions)
    );
  };
}
