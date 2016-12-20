import {pipe} from './pipe';
import {get} from './get';
import {addChildren} from './jsx';
import {runUpdate} from './runUpdate';

export function Select({selector, children}) {
  if (typeof selector !== 'function' && typeof selector !== 'string') {
    throw new Error('selector should be function or property name');
  }

  if (typeof selector === 'string') {
    return Select({
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
