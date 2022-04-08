/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';

import './ClickBehavior.scss';

export default class ClickBehavior extends React.Component {
  static defaultProps = {
    nodeTypes: ['FlowNode'],
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
    elementRegistry.forEach((element) => {
      if (this.isValid(element)) {
        canvas.removeMarker(element.businessObject.id, 'ClickBehavior__node--selected');
        canvas.removeMarker(element.businessObject.id, 'ClickBehavior__node');
      } else if (this.isDisabled(element.businessObject)) {
        canvas.removeMarker(element.businessObject.id, 'ClickBehavior__disabled');
      }
    });
  };

  isDisabled = (el) =>
    el.$instanceOf('bpmn:FlowNode') ||
    el.$instanceOf('bpmn:SequenceFlow') ||
    el.$instanceOf('bpmn:Lane') ||
    el.$instanceOf('bpmn:Participant');

  update() {
    const {viewer, selectedNodes} = this.props;
    const elementRegistry = viewer.get('elementRegistry');
    const canvas = viewer.get('canvas');

    // remove existing selection markers and indicate selectable status for all flownodes
    elementRegistry.forEach((element) => {
      if (this.isValid(element)) {
        canvas.removeMarker(element.businessObject.id, 'ClickBehavior__node--selected');
        canvas.addMarker(element.businessObject.id, 'ClickBehavior__node');
      } else if (this.isDisabled(element.businessObject)) {
        canvas.addMarker(element.businessObject.id, 'ClickBehavior__disabled');
      }
    });

    // add selection marker for all selected nodes
    selectedNodes.forEach((elementId) => {
      canvas.addMarker(elementId, 'ClickBehavior__node--selected');
    });
  }

  getNodeObjects = () => {
    const {viewer, selectedNodes} = this.props;
    const elementRegistry = viewer.get('elementRegistry');
    const nodes = selectedNodes.map((v) => elementRegistry.get(v).businessObject);
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

  isValid = (element) =>
    this.props.nodeTypes.some((type) => element.businessObject.$instanceOf('bpmn:' + type));

  onClick = ({element}) => {
    if (this.isValid(element)) {
      this.props.onClick(element.businessObject);
    }
  };

  onHover = ({element}) => {
    if (this.props.onHover && this.isValid(element)) {
      this.props.onHover(element.businessObject);
    }
  };

  outHandler = ({element}) => {
    if (this.props.onHover && this.isValid(element)) {
      this.props.onHover(null);
    }
  };

  setupEventListeners() {
    this.props.viewer.on('element.click', this.onClick);
    this.props.viewer.on('element.hover', this.onHover);
    this.props.viewer.on('element.out', this.outHandler);
  }

  teardownEventListeners = () => {
    this.props.viewer.off('element.click', this.onClick);
    this.props.viewer.off('element.hover', this.onHover);
    this.props.viewer.off('element.out', this.outHandler);
  };
}
