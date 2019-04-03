/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import './TargetValueDiagramBehavior.scss';

export default class TargetValueDiagramBehavior extends React.Component {
  render() {
    const {viewer, focus} = this.props;

    if (viewer) {
      const elementRegistry = viewer.get('elementRegistry');
      const canvas = viewer.get('canvas');

      elementRegistry.forEach(({businessObject}) => {
        if (businessObject.id === focus) {
          canvas.addMarker(businessObject.id, 'TargetValueDiagramBehavior__highlight');
        } else {
          canvas.removeMarker(businessObject.id, 'TargetValueDiagramBehavior__highlight');
        }
      });
    }
    return null;
  }

  isValidNode = element => element.$instanceOf('bpmn:' + this.props.nodeType);

  componentDidMount() {
    const {viewer} = this.props;
    const elementRegistry = viewer.get('elementRegistry');
    const canvas = viewer.get('canvas');

    // indicate selectable status for all valid flownodes
    elementRegistry.forEach(({businessObject}) => {
      if (this.isValidNode(businessObject)) {
        canvas.addMarker(businessObject.id, 'TargetValueDiagramBehavior__clickable');

        const gfx = elementRegistry.getGraphics(businessObject.id).querySelector('.djs-outline');

        gfx.setAttribute('rx', '14px');
        gfx.setAttribute('ry', '14px');
      }
    });

    viewer.get('eventBus').on('element.click', this.clickHandler);
  }

  componentWillUnmount() {
    const {viewer} = this.props;
    const elementRegistry = viewer.get('elementRegistry');
    const canvas = viewer.get('canvas');

    // remove selectable selectable status indication and highlights for all valid flownodes
    elementRegistry.forEach(({businessObject}) => {
      canvas.removeMarker(businessObject.id, 'TargetValueDiagramBehavior__clickable');
      canvas.removeMarker(businessObject.id, 'TargetValueDiagramBehavior__highlight');
    });
    viewer.get('eventBus').off('element.click', this.clickHandler);
  }

  clickHandler = ({element}) => {
    if (
      element &&
      element.businessObject &&
      element.businessObject.$instanceOf('bpmn:' + this.props.nodeType)
    ) {
      this.props.onClick(element.businessObject.id);
    }
  };
}
