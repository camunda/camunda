import {addChild, addChildren} from './jsx';
import {runUpdate} from './runUpdate';
import {updateOnlyWhenStateChanges} from './updateOnlyWhenStateChanges';
import {DESTROY_EVENT} from './events';
import {$document} from './dom';
import {insertAfter} from './insertAfter';

const truePredicate = () => true;

export function Match({didStateChange, children}) {
  assertAllChildrenAreCase(children);

  return (node, eventsBus) => {
    let lastChild;
    let update;
    let childNodes = [];
    const startMarker = $document.createComment('START MATCH');

    node.append(startMarker);

    return updateOnlyWhenStateChanges(
      (state) => {
        const child = findChild(children, state);

        if (child) {
          if (child !== lastChild) {
            ({update, childNodes} = replaceChild(startMarker, childNodes, update, eventsBus, child));
            lastChild = child;
          }

          runUpdate(update, state);
        } else {
          removeChild(childNodes, update);
          update = undefined;
          lastChild = undefined;
          childNodes = [];
        }
      },
      didStateChange
    );
  };
}

function findChild(children, state) {
  return children.find(({predicate}) => predicate(state));
}

function replaceChild(startMarker, childNodes, update, eventsBus, child) {
  removeChild(childNodes, update);

  const fragment = document.createDocumentFragment();
  const newUpdate = addChild(fragment, eventsBus, child, true);
  const newChildNodes = Array.prototype.slice.call(fragment.childNodes);

  insertAfter(fragment, startMarker);

  return {
    update: newUpdate,
    childNodes: newChildNodes
  };
}

function removeChild(childNodes, {eventsBus} = {}) {
  childNodes.forEach(node => node.parentNode.removeChild(node));

  if (eventsBus) {
    eventsBus.fireEvent(DESTROY_EVENT, {});
  }
}

function assertAllChildrenAreCase(children) {
  return children.every(({predicate}) => typeof predicate === 'function');
}

export function Case({predicate, children}) {
  const component = (node, eventsBus) => {
    return addChildren(node, eventsBus, children);
  };

  component.predicate = predicate;

  return component;
}

export function Default({children}) {
  const component = (node, eventsBus) => {
    return addChildren(node, eventsBus, children);
  };

  component.predicate = truePredicate;

  return component;
}
