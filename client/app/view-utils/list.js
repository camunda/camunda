import {includes} from './includes';
import {addChildren} from './jsx';
import {runUpdate} from './runUpdate';
import {DESTROY_EVENT} from './events';
import {$document} from './dom';
import {insertAfter} from './insertAfter';

export function List({onlyChild = false, key, children}) {
  return (parent, eventsBus) => {
    let nodes = [];
    const startMarker = $document.createComment('LIST START');

    parent.appendChild(startMarker);

    return (values) => {
      const valuesWithKey = values.map(
        wrapWithKey.bind(null, key)
      );

      assertKeysAreUnique(valuesWithKey);

      nodes = render(
        startMarker,
        nodes,
        valuesWithKey,
        getNewNode.bind(null, eventsBus, children),
        onlyChild ? parent : null
      );
    };
  };
}

function assertKeysAreUnique(valuesWithKey) {
  const keys = valuesWithKey.map(({key}) => key);

  for (let key; keys.length > 0; key = keys.shift()) {
    if (includes(keys, key)) {
      throw new Error(`key ${key} is not unique`);
    }
  }
}

function wrapWithKey(keyProperty, value, index) {
  if (!keyProperty) {
    return {
      key: index,
      value: value
    };
  }

  return {
    key: value[keyProperty],
    value: value
  };
}

function getNewNode(eventsBus, children) {
  const node = document.createDocumentFragment();
  const updates = addChildren(node, eventsBus, children, true);
  const update = runUpdate.bind(
    null,
    updates
  );

  return {
    children: Array.prototype.slice.call(node.childNodes),
    update,
    fireEvent: fireEvent.bind(null, updates)
  };
}

function fireEvent(updates, name, data) {
  updates
    .forEach(({eventsBus}) => {
      eventsBus.fireEvent(name, data);
    });
}

function render(startMarker, nodes, valuesWithKey, getNewNode, parent) {
  if (nodes.length) {
    removeNodes(nodes, parent);
  }

  if (valuesWithKey.length > 0) {
    return insertValueNodes(startMarker, nodes, valuesWithKey, getNewNode, parent);
  } else {
    fireDestroyEventForNotUsed(nodes);

    return [];
  }
}

function removeNodes(nodes, parent) {
  if (!parent) {
    nodes.forEach(({children}) => {
      children.forEach(node => node.parentNode.removeChild(node));
    });
  } else {
    parent.innerHTML = '';
  }
}

function insertValueNodes(startMarker, nodes, valuesWithKey, getNewNode, parent) {
  const notUsed = splitNodes(nodes, valuesWithKey);

  const fragment = document.createDocumentFragment();

  const newNodes = valuesWithKey.map(({node: updatedNode, key, value}) => {
    const {children, update, ...rest} = updatedNode || getNextNode();

    children.forEach(node => {
      fragment.appendChild(node);
    });

    return {
      ...rest,
      children,
      update,
      value,
      key
    };
  });

  if (parent) {
    parent.appendChild(fragment);
  } else {
    insertAfter(fragment, startMarker);
  }

  newNodes.forEach(({update, value}) => {
    update(value);
  });

  fireDestroyEventForNotUsed(notUsed);

  return newNodes;

  function getNextNode() {
    return notUsed.shift() || getNewNode();
  }
}

function fireDestroyEventForNotUsed(notUsed) {
  notUsed.forEach(({fireEvent}) => fireEvent(DESTROY_EVENT, {}));
}

function splitNodes(nodes, valuesWithKey) {
  const nodesByKey = nodes.reduce((nodesByKey, node) => {
    nodesByKey[node.key] = node;

    return nodesByKey;
  }, {});

  valuesWithKey
    .forEach(valueWithKey => {
      valueWithKey.node = nodesByKey[valueWithKey.key];
      delete nodesByKey[valueWithKey.key];
    });

  return Object
    .keys(nodesByKey)
    .map(key => nodesByKey[key]);
}
