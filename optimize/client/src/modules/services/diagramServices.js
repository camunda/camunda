/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

export function getDiagramElementsBetween(start, end, viewer) {
  const reachableNodes = [];

  // find all the parents of both start and end node (nodes could be in subprocesses)
  const startStack = createParentStack(start);
  const endStack = createParentStack(end);

  // find the deepest (sub-)process that contains both start and end node
  const commonParent = startStack.find((parent) => endStack.includes(parent));

  // throw away all subprocesses further up, that also contain both nodes
  startStack.length = startStack.indexOf(commonParent);
  endStack.length = endStack.indexOf(commonParent);

  // work from the top down
  startStack.reverse();
  endStack.reverse();

  if (startStack.length && endStack.length) {
    // on top level, we actually are interested in the nodes between the elements
    reachableNodes.push(...getNodesBetween(startStack.shift(), endStack.shift(), viewer));
  } else {
    // SPECIAL CASE: When one of the nodes contains the other, which can happen if one of those is a subprocess
    // in that case, we only highlight the containing node

    if (startStack.length === 0) {
      reachableNodes.push(start.id);
    }
    if (endStack.length === 0) {
      reachableNodes.push(end.id);
    }
  }

  // only if there is a way between the two top level nodes
  if (reachableNodes.length) {
    // on lower levels, we are only interested in nodes _after_ the start node and _before_ the end node
    startStack.forEach((node) => reachableNodes.push(...getNodesAfter(node, viewer)));
    endStack.forEach((node) => reachableNodes.push(...getNodesBefore(node, viewer)));

    // add all nested nodes of the selected end, except if the start is inside the end
    const nestedInEnd = getNestedNodes(end);
    if (!nestedInEnd.includes(start.id)) {
      reachableNodes.push(...nestedInEnd);
    }

    // add all nested nodes of the selected start, except if the end is inside the start
    const nestedInStart = getNestedNodes(start);
    if (!nestedInStart.includes(end.id)) {
      reachableNodes.push(...nestedInStart);
    }
  }

  return reachableNodes;
}

function createParentStack(node) {
  const stack = [];
  let current = node;
  while (current) {
    stack.push(current);
    current = current.$parent;
  }

  return stack;
}

function getNodesBetween(start, end, viewer) {
  const reachableNodes = [];

  const registry = viewer.get('elementRegistry');

  const graph = constructReachabilityGraphFrom(start, end, viewer);

  // use backlinks from end node to find nodes between start and end
  if (graph[end.id]) {
    const unprocessed = [end.id];

    while (unprocessed.length > 0) {
      const current = unprocessed.shift();
      reachableNodes.push(current);

      if (current !== start.id && current !== end.id) {
        reachableNodes.push(...getNestedNodes(registry.get(current).businessObject));
      }

      graph[current] &&
        graph[current].forEach(({node, via}) => {
          via && reachableNodes.push(via);
          if (!reachableNodes.includes(node) && !unprocessed.includes(node)) {
            unprocessed.push(node);
          }
        });
    }
  }

  return reachableNodes;
}

function constructReachabilityGraphFrom(start, end, viewer) {
  const boundaries = getBoundaries(viewer);
  const unprocessed = [start];
  const graph = {};

  while (unprocessed.length > 0) {
    const current = unprocessed.shift();

    const boundariesOnCurrent = boundaries.filter(({attachedToRef}) => attachedToRef === current);
    boundariesOnCurrent.forEach((boundary) => {
      unprocessed.push(boundary);
      graph[boundary.id] = [{node: current.id}];
    });

    current.outgoing &&
      current.outgoing.forEach((connection) => {
        const target = connection.targetRef;
        const targetId = target.id;

        if (!graph[targetId]) {
          graph[targetId] = [];
          if (target !== end) {
            unprocessed.push(target);
          }
        }

        graph[targetId].push({node: current.id, via: connection.id});
      });
  }

  return graph;
}

function getBoundaries(viewer) {
  return viewer
    .get('elementRegistry')
    .filter((element) => element.businessObject.$instanceOf('bpmn:BoundaryEvent'))
    .map((element) => element.businessObject);
}

function getNodesAfter(node, viewer) {
  return getNodes('after', node, viewer);
}

function getNodesBefore(node, viewer) {
  return getNodes('before', node, viewer);
}

function getNodes(mode, node, viewer) {
  const direction = mode === 'before' ? 'incoming' : 'outgoing';
  const refType = mode === 'before' ? 'sourceRef' : 'targetRef';

  const boundaries = getBoundaries(viewer);
  const reachableNodes = [];
  const unprocessed = [node];

  while (unprocessed.length > 0) {
    const current = unprocessed.shift();
    reachableNodes.push(current.id);

    if (current !== node) {
      reachableNodes.push(...getNestedNodes(current));
    }

    if (mode === 'after') {
      const boundariesOnCurrent = boundaries.filter(({attachedToRef}) => attachedToRef === current);
      boundariesOnCurrent.forEach((boundary) => {
        unprocessed.push(boundary);
      });
    }

    if (current.$instanceOf('bpmn:BoundaryEvent') && mode === 'before') {
      unprocessed.push(current.attachedToRef);
    }

    current[direction] &&
      current[direction].forEach((connection) => {
        const target = connection[refType];
        const targetId = target.id;

        reachableNodes.push(connection.id);

        if (!reachableNodes.includes(targetId) && !unprocessed.includes(target)) {
          unprocessed.push(target);
        }
      });
  }

  return reachableNodes;
}

function getNestedNodes(node) {
  const nested = [];

  (node.flowElements || []).forEach((nestedNode) => {
    nested.push(nestedNode.id, ...getNestedNodes(nestedNode));
  });

  return nested;
}
