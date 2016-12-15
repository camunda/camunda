import {addChild, addChildren} from './jsx';
import {runUpdate} from './runUpdate';
import {updateOnlyWhenStateChanges} from './updateOnlyWhenStateChanges';
import {DESTROY_EVENT} from './events';

const truePredicate = () => true;

export function Match({didStateChange, children}) {
  assertAllChildrenAreCase(children);

  return (node, eventsBus) => {
    let lastChild;
    let update;

    return updateOnlyWhenStateChanges(
      (state) => {
        const child = findChild(children, state);

        if (child) {
          if (child !== lastChild) {
            update = replaceChild(node, update, eventsBus, child);
            lastChild = child;
          }

          runUpdate(update, state);
        } else {
          removeChild(node, update);
          update = undefined;
          lastChild = undefined;
        }
      },
      didStateChange
    );
  };
}

function findChild(children, state) {
  for (let child of children) {
    if (child.predicate(state)) {
      return child;
    }
  }
}

function replaceChild(node, update, eventsBus, child) {
  removeChild(node, update);

  return addChild(node, eventsBus, child, true);
}

function removeChild(node, {eventsBus} = {}) {
  node.innerHTML = '';

  if (eventsBus) {
    eventsBus.fireEvent(DESTROY_EVENT, {});
  }
}

function assertAllChildrenAreCase(children) {
  return children.every(({predicate}) => typeof predicate === 'function');
}

export function Case({predicate, isDefault = false, children}) {
  const component = (node, eventsBus) => {
    return addChildren(node, eventsBus, children);
  };

  component.predicate = isDefault ? truePredicate() : predicate;

  return component;
}
