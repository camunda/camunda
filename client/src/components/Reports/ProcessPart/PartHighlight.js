import React from 'react';

import './PartHighlight.css';

export default class PartHighlight extends React.Component {
  componentDidMount() {
    // add highlighted sequence flow end marker
    const {viewer} = this.props;

    this.defs = viewer._container.querySelector('defs');
    this.highlightMarker = this.defs.querySelector('marker').cloneNode(true);

    this.highlightMarker.setAttribute('id', 'sequenceflow-end-highlight');

    this.defs.appendChild(this.highlightMarker);
  }

  getBoundaries = () =>
    this.props.viewer
      .get('elementRegistry')
      .filter(element => element.businessObject.$instanceOf('bpmn:BoundaryEvent'))
      .map(element => element.businessObject);

  getNestedNodes = node => {
    const nested = [];

    (node.flowElements || []).forEach(nestedNode => {
      nested.push(nestedNode.id, ...this.getNestedNodes(nestedNode));
    });

    return nested;
  };

  getNodesBetween = (start, end) => {
    const boundaries = this.getBoundaries();
    const reachableNodes = [];

    const registry = this.props.viewer.get('elementRegistry');

    const graph = {};
    const unprocessed = [start];

    // phase 1: construct reachability graph from start node
    while (unprocessed.length > 0) {
      const current = unprocessed.shift();

      const boundary = boundaries.find(({attachedToRef}) => attachedToRef === current);
      if (boundary) {
        unprocessed.push(boundary);
      }

      current.outgoing &&
        current.outgoing.forEach(connection => {
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

    // phase 2: use backlinks from end node to find nodes between start and end
    if (graph[end.id]) {
      const unprocessed = [end.id];

      while (unprocessed.length > 0) {
        const current = unprocessed.shift();
        reachableNodes.push(current);

        if (current !== start.id && current !== end.id) {
          reachableNodes.push(...this.getNestedNodes(registry.get(current).businessObject));
        }

        graph[current] &&
          graph[current].forEach(({node, via}) => {
            reachableNodes.push(via);
            if (!reachableNodes.includes(node) && !unprocessed.includes(node)) {
              unprocessed.push(node);
            }
          });
      }
    }

    return reachableNodes;
  };

  createParentStack = node => {
    const stack = [];
    let current = node;
    while (current) {
      stack.push(current);
      current = current.$parent;
    }

    return stack;
  };

  getNodes = mode => node => {
    const direction = mode === 'before' ? 'incoming' : 'outgoing';
    const refType = mode === 'before' ? 'sourceRef' : 'targetRef';

    const boundaries = this.getBoundaries();
    const reachableNodes = [];
    const unprocessed = [node];

    while (unprocessed.length > 0) {
      const current = unprocessed.shift();
      reachableNodes.push(current.id);

      if (current !== node) {
        reachableNodes.push(...this.getNestedNodes(current));
      }

      const boundary = boundaries.find(({attachedToRef}) => attachedToRef === current);
      if (boundary) {
        unprocessed.push(boundary);
      }

      current[direction] &&
        current[direction].forEach(connection => {
          const target = connection[refType];
          const targetId = target.id;

          reachableNodes.push(connection.id);

          if (!reachableNodes.includes(targetId) && !unprocessed.includes(target)) {
            unprocessed.push(target);
          }
        });
    }

    return reachableNodes;
  };

  getNodesBefore = this.getNodes('before');
  getNodesAfter = this.getNodes('after');

  render() {
    const {nodes, viewer} = this.props;
    let reachableNodes = [];

    // only if start and end are selected and are resolved to bpmn-js objects
    if (nodes.length === 2 && typeof nodes[0] === 'object') {
      // find all the parents of both start and end node (nodes could be in subprocesses)
      const startStack = this.createParentStack(nodes[0]);
      const endStack = this.createParentStack(nodes[1]);

      // find the deepest (sub-)process that contains both start and end node
      const commonParent = startStack.find(parent => endStack.includes(parent));

      // throw away all subprocesses further up, that also contain both nodes
      startStack.length = startStack.indexOf(commonParent);
      endStack.length = endStack.indexOf(commonParent);

      // work from the top down
      startStack.reverse();
      endStack.reverse();

      if (startStack.length && endStack.length) {
        // on top level, we actually are interested in the nodes between the elements
        reachableNodes.push(...this.getNodesBetween(startStack.shift(), endStack.shift()));
      } else {
        // SPECIAL CASE: When one of the nodes contains the other, which can happen if one of those is a subprocess
        // in that case, we only highlight the containing node

        if (startStack.length === 0) {
          reachableNodes.push(nodes[0].id);
        }
        if (endStack.length === 0) {
          reachableNodes.push(nodes[1].id);
        }
      }

      // only if there is a way between the two top level nodes
      if (reachableNodes.length) {
        // on lower levels, we are only interested in nodes _after_ the start node and _before_ the end node
        startStack.forEach(node => reachableNodes.push(...this.getNodesAfter(node)));
        endStack.forEach(node => reachableNodes.push(...this.getNodesBefore(node)));

        // add all nested nodes of the selected end, except if the start is inside the end
        const nestedInEnd = this.getNestedNodes(nodes[1]);
        if (!nestedInEnd.includes(nodes[0].id)) {
          reachableNodes.push(...nestedInEnd);
        }
      }

      this.props.setHasPath(reachableNodes.length > 0);
    }

    const canvas = viewer.get('canvas');

    // adjust markers
    viewer.get('elementRegistry').forEach(({businessObject, id}) => {
      canvas.removeMarker(id, 'PartHighlight');
      if (reachableNodes.includes(businessObject.id)) {
        canvas.addMarker(id, 'PartHighlight');
      }
    });

    return null;
  }

  componentWillUnmount() {
    const {viewer} = this.props;

    viewer.get('elementRegistry').forEach(({id}) => {
      viewer.get('canvas').removeMarker(id, 'PartHighlight');
    });

    this.defs.removeChild(this.highlightMarker);
  }
}
