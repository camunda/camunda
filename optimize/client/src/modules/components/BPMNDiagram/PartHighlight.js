/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import {getDiagramElementsBetween} from 'services';

import './PartHighlight.scss';

export default class PartHighlight extends React.Component {
  static defaultProps = {
    setHasPath: () => {},
  };

  render() {
    const {nodes, viewer} = this.props;
    let reachableNodes = [];

    // only if start and end are selected and are resolved to bpmn-js objects
    if (nodes.length === 2 && typeof nodes[0] === 'object') {
      reachableNodes = getDiagramElementsBetween(nodes[0], nodes[1], viewer);
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
  }
}
