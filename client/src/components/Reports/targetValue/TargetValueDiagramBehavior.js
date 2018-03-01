import React from 'react';

import './TargetValueDiagramBehavior.css';

export default class TargetValueDiagramBehavior extends React.Component {
  render() {
    const {viewer, focus} = this.props;

    if(viewer) {
      const elementRegistry = viewer.get('elementRegistry');
      const canvas = viewer.get('canvas');

      elementRegistry.forEach(({businessObject}) => {
        if(businessObject.id === focus) {
          canvas.addMarker(businessObject.id, 'TargetValueDiagramBehavior__highlight');
        } else {
          canvas.removeMarker(businessObject.id, 'TargetValueDiagramBehavior__highlight');
        }
      });
    }
    return null;
  }

  isValidNode = element => element.$instanceOf('bpmn:FlowNode');

  componentDidMount() {
    const {viewer} = this.props;
    const elementRegistry = viewer.get('elementRegistry');
    const canvas = viewer.get('canvas');

    // indicate selectable status for all valid flownodes
    elementRegistry.forEach(({businessObject}) => {
      if(this.isValidNode(businessObject)) {
        canvas.addMarker(businessObject.id, 'TargetValueDiagramBehavior__clickable');

        const gfx = elementRegistry.getGraphics(businessObject.id).querySelector('.djs-outline');

        gfx.setAttribute('rx', '14px');
        gfx.setAttribute('ry', '14px');
      }
    });

    viewer.get('eventBus').on('element.click', this.clickHandler);
  }

  componentWillUnmount() {
    this.props.viewer.get('eventBus').off('element.click', this.clickHandler);
  }

  clickHandler = ({element}) => {
    if(element && element.businessObject && element.businessObject.$instanceOf('bpmn:FlowNode')) {
      this.props.onClick(element.businessObject.id);
    }
  }
}
