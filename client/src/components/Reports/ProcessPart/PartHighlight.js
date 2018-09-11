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

  render() {
    const {nodes, viewer} = this.props;
    const reachableNodes = new Set();

    if (nodes.length === 2 && typeof nodes[0] === 'object') {
      // only if start and end are selected and are resolved to bpmn-js objects

      const graph = {};
      const unprocessed = [nodes[0]];

      // phase 1: construct reachability graph from start node
      while (unprocessed.length > 0) {
        const current = unprocessed.shift();

        current.outgoing &&
          current.outgoing.forEach(connection => {
            const targetId = connection.targetRef.id;

            if (!graph[targetId]) {
              graph[targetId] = [];
              unprocessed.push(connection.targetRef);
            }

            graph[targetId].push({node: current.id, via: connection.id});
          });
      }

      // phase 2: use backlinks from end node to find nodes between start and end
      if (graph[nodes[1].id]) {
        const unprocessed = [nodes[1].id];

        while (unprocessed.length > 0) {
          const current = unprocessed.shift();
          reachableNodes.add(current);

          graph[current] &&
            graph[current].forEach(({node, via}) => {
              reachableNodes.add(via);
              if (!reachableNodes.has(node) && !unprocessed.includes(node)) {
                unprocessed.push(node);
              }
            });
        }
      }
    }

    const canvas = viewer.get('canvas');

    // adjust markers
    viewer.get('elementRegistry').forEach(({businessObject, id}) => {
      canvas.removeMarker(id, 'PartHighlight');
      if (reachableNodes.has(businessObject.id)) {
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
