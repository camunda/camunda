import {includes} from './includes';
import {addChildren} from './jsx';
import {runUpdate} from './runUpdate';
import {DESTROY_EVENT} from './events';

export function List({key, children}) {
  return (templateNode, eventsBus) => {
    let nodes = [];
    let parent = templateNode.parentNode;
    const startMarker = document.createComment('LIST START');

    parent.insertBefore(startMarker, templateNode);
    parent.removeChild(templateNode);

    return (values) => {
      const valuesWithKey = values.map(
        wrapWithKey.bind(null, key)
      );

      assertKeysAreUnique(valuesWithKey);

      nodes = render(
        startMarker,
        nodes,
        valuesWithKey,
        getNewNode.bind(null, templateNode, eventsBus, children)
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

function getNewNode(templateNode, eventsBus, children) {
  const node = templateNode.cloneNode(true);
  const updates = addChildren(node, eventsBus, children);
  const update = runUpdate.bind(
    null,
    updates
  );

  return {
    node,
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
  removeNodes(nodes);

  return insertValueNodes(startMarker, nodes, valuesWithKey, getNewNode);
}

function removeNodes(nodes) {
  nodes.forEach(({node}) => node.parentNode.removeChild(node));
}

function insertValueNodes(startMarker, nodes, valuesWithKey, getNewNode) {
  const {updated, notUsed} = splitNodes(nodes, valuesWithKey);
  const startValue = {
    nodes: [],
    previous: startMarker
  };

  const {nodes: newNodes} = valuesWithKey.reduce(({nodes, previous}, {key, value}) => {
    const {node, update, ...rest} = getNextNode(key);

    insertAfter(node, previous);

    update(value);

    nodes.push({
      ...rest,
      node,
      update,
      key
    });

    return {
      nodes,
      previous: node
    };
  }, startValue);

  fireDestroyEventForNotUsed(notUsed);

  return newNodes;

  function getNextNode(key) {
    return updated[key] || notUsed.shift() || getNewNode();
  }
}

function fireDestroyEventForNotUsed(notUsed) {
  notUsed.forEach(({fireEvent}) => fireEvent(DESTROY_EVENT))
}

function splitNodes(nodes, valuesWithKey) {
  const startValue = {
    updated: {},
    notUsed: []
  };
  const valuesKeys = valuesWithKey.map(({key}) => key);

  return nodes
    .reduce(({updated, notUsed}, nodeEntry) => {
      if (nodeEntry.key && includes(valuesKeys, nodeEntry.key)) {
        updated[nodeEntry.key] = nodeEntry;
      } else {
        notUsed.push(nodeEntry);
      }

      return {updated, notUsed};
    }, startValue);
}

function insertAfter(node, target) {
  target.parentNode.insertBefore(node, target.nextSibling);
}
