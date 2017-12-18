import React from 'react';

import './ClickBehavior.css';

export default class ClickBehavior extends React.Component {
  render() {
    return null;
  }

  componentDidMount() {
    this.setupEventListeners();
    this.update();
  }

  componentDidUpdate() {
    this.update();
  }

  update() {
    const {viewer, selectedNodes} = this.props;
    const elementRegistry = viewer.get('elementRegistry');
    const canvas = viewer.get('canvas');

    // remove existing selection markers and indicate selectable status for all flownodes
    elementRegistry.forEach(element => {
      if(element.businessObject.$instanceOf('bpmn:FlowNode')) {
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

  setupEventListeners() {
    this.props.viewer.on('element.click', ({element}) => {
      if(element.businessObject.$instanceOf('bpmn:FlowNode')) {
        const node = {
          "id": element.businessObject.id,
          "name": element.businessObject.name
        }
        this.props.onClick(node);
      }
    });
  }
}
