import {includes} from './includes';
import {addChildren} from './jsx';
import {runUpdate} from './runUpdate';
import {DESTROY_EVENT} from './events';
import {$document} from './dom';
import {createProxyNode} from './createProxyNode';

export function List({key, children}) {
  return (parent, eventsBus) => {
    let nodes = [];
    const startMarker = $document.createComment('LIST START');

    parent.appendChild(startMarker);

    return (values = []) => {
      const valuesWithKey = values.map(
        wrapWithKey.bind(null, key)
      );

      assertKeysAreUnique(valuesWithKey);

      nodes = render(
        startMarker,
        nodes,
        valuesWithKey,
        getNewNode.bind(null, parent, eventsBus, children)
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

function getNewNode(parent, eventsBus, children, startMarker) {
  const proxyNode = createProxyNode(parent, startMarker);
  const updates = addChildren(proxyNode, eventsBus, children, true);
  const update = runUpdate.bind(
    null,
    updates
  );

  return {
    proxyNode,
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

function render(startMarker, nodes, valuesWithKey, getNewNode) {
  if (valuesWithKey.length > 0) {
    return insertValueNodes(startMarker, nodes, valuesWithKey, getNewNode);
  } else {
    fireDestroyEventForNotUsed(nodes);

    return [];
  }
}

function insertValueNodes(startMarker, nodes, valuesWithKey, getNewNode) {
  const notUsed = splitNodes(nodes, valuesWithKey);

  let currentStartNode = startMarker;

  const newNodes = valuesWithKey.map(({node: updatedNode, key, value}) => {
    const {proxyNode, update, ...rest} = getNextNode(updatedNode);

    return {
      ...rest,
      proxyNode,
      update,
      value,
      key
    };
  });

  newNodes.forEach(({update, value}) => {
    update(value);
  });

  fireDestroyEventForNotUsed(notUsed);

  return newNodes;

  function getNextNode(updatedNode) {
    const reusedNode = updatedNode || notUsed.shift();

    if (reusedNode) {
      const {proxyNode} = reusedNode;
      const children = proxyNode.removeChildren();

      proxyNode.setStartMarker(currentStartNode);
      children.forEach(proxyNode.appendChild);

      currentStartNode = children[children.length - 1] || currentStartNode;

      return reusedNode;
    }

    const newNode = getNewNode(currentStartNode);
    const {proxyNode: {childNodes}} = newNode;

    currentStartNode = childNodes[childNodes.length - 1] || currentStartNode;

    return newNode;
  }
}

function fireDestroyEventForNotUsed(notUsed) {
  notUsed.forEach(({fireEvent, proxyNode}) => {
    proxyNode.removeChildren();
    fireEvent(DESTROY_EVENT, {});
  });
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
