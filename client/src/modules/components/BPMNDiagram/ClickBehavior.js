/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import './ClickBehavior.scss';

export default class ClickBehavior extends React.Component {
  static defaultProps = {
    nodeType: 'FlowNode'
  };

  render() {
    return null;
  }

  componentDidMount() {
    if (this.props.setSelectedNodes) {
      this.getNodeObjects();
    }
    this.setupEventListeners();
    this.update();
    this.roundEdges();
  }

  componentWillUnmount() {
    this.teardownEventListeners();
    this.removeMarkers();
  }

  componentDidUpdate() {
    this.update();
  }

  removeMarkers = () => {
    const {viewer} = this.props;
    const elementRegistry = viewer.get('elementRegistry');
    const canvas = viewer.get('canvas');

    // remove existing selection markers
    elementRegistry.forEach(element => {
      if (element.businessObject.$instanceOf('bpmn:' + this.props.nodeType)) {
        canvas.removeMarker(element.businessObject.id, 'ClickBehavior__node--selected');
        canvas.removeMarker(element.businessObject.id, 'ClickBehavior__node');
      }
    });
  };

  onClick = ({element}) => {
    if (element.businessObject.$instanceOf('bpmn:' + this.props.nodeType)) {
      this.props.onClick(element.businessObject);
    }
  };

  teardownEventListeners = () => {
    this.props.viewer.off('element.click', this.onClick);
  };

  update() {
    const {viewer, selectedNodes} = this.props;
    const elementRegistry = viewer.get('elementRegistry');
    const canvas = viewer.get('canvas');

    // remove existing selection markers and indicate selectable status for all flownodes
    elementRegistry.forEach(element => {
      if (element.businessObject.$instanceOf('bpmn:' + this.props.nodeType)) {
        canvas.removeMarker(element.businessObject.id, 'ClickBehavior__node--selected');
        canvas.addMarker(element.businessObject.id, 'ClickBehavior__node');
      }
    });

    // add selection marker for all selected nodes
    selectedNodes.forEach(elementId => {
      canvas.addMarker(elementId, 'ClickBehavior__node--selected');
    });
  }

  getNodeObjects = () => {
    const {viewer, selectedNodes} = this.props;
    const elementRegistry = viewer.get('elementRegistry');
    const nodes = selectedNodes.map(v => elementRegistry.get(v).businessObject);
    this.props.setSelectedNodes(nodes);
  };

  roundEdges = () => {
    this.props.viewer.get('elementRegistry').forEach((element, gfx) => {
      const outline = gfx.querySelector('.djs-outline');
      if (outline) {
        outline.setAttribute('rx', '14px');
        outline.setAttribute('ry', '14px');
      }
    });
  };

  setupEventListeners() {
    this.props.viewer.on('element.click', this.onClick);
  }
}
