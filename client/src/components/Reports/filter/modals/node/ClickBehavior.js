import React from 'react';

import './ClickBehavior.css';

export default class ClickBehavior extends React.Component {
  render() {
    return null;
  }

  componentDidMount() {
    this.getNodeObjects();
    this.setupEventListeners();
    this.update();
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
      if (element.businessObject.$instanceOf('bpmn:FlowNode')) {
        canvas.removeMarker(element.businessObject.id, 'ClickBehavior__node--selected');
        canvas.removeMarker(element.businessObject.id, 'ClickBehavior__node');
      }
    });
  };

  onClick = ({element}) => {
    if (element.businessObject.$instanceOf('bpmn:FlowNode')) {
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
      if (element.businessObject.$instanceOf('bpmn:FlowNode')) {
        canvas.removeMarker(element.businessObject.id, 'ClickBehavior__node--selected');
        canvas.addMarker(element.businessObject.id, 'ClickBehavior__node');
      }
    });

    // add selection marker for all selected nodes
    selectedNodes.forEach(elementId => {
      canvas.addMarker(elementId, 'ClickBehavior__node--selected');

      const gfx = elementRegistry.getGraphics(elementId).querySelector('.djs-outline');

      gfx.setAttribute('rx', '14px');
      gfx.setAttribute('ry', '14px');
    });
  }

  getNodeObjects = () => {
    const {viewer, selectedNodes} = this.props;
    const elementRegistry = viewer.get('elementRegistry');
    const nodes = selectedNodes.map(v => elementRegistry.get(v).businessObject);
    this.props.setSelectedNodes(nodes);
  };

  setupEventListeners() {
    this.props.viewer.on('element.click', this.onClick);
  }
}
