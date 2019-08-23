/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';

import {getDiagramElementsBetween} from 'services';
import {formatters} from 'services';
import {t} from 'translation';

import './DiagramBehavior.scss';

export default class DiagramBehavior extends React.Component {
  render() {
    // clear previously highlighted nodes
    const {viewer, hoveredControl, hoveredNode, data, gateway, endEvent} = this.props;
    if (viewer) {
      const elementRegistry = viewer.get('elementRegistry');
      const canvas = viewer.get('canvas');

      // remove existing selection markers and indicate selectable status for all flownodes
      elementRegistry.forEach(element => {
        canvas.removeMarker(element, 'analysis-part-highlight');

        const {businessObject} = element;
        if (this.isValidNode(businessObject)) {
          canvas.removeMarker(businessObject.id, 'DiagramBehavior__highlight');
          canvas.removeMarker(businessObject.id, 'DiagramBehavior__selected');

          if (hoveredControl === 'gateway' && businessObject.$instanceOf('bpmn:Gateway')) {
            canvas.addMarker(businessObject.id, 'DiagramBehavior__highlight');
          } else if (hoveredControl === 'endEvent' && businessObject.$instanceOf('bpmn:EndEvent')) {
            canvas.addMarker(businessObject.id, 'DiagramBehavior__highlight');
          }

          if (businessObject === hoveredNode) {
            canvas.addMarker(businessObject.id, 'DiagramBehavior__highlight');
          }
          if (businessObject === gateway || businessObject === endEvent) {
            canvas.addMarker(businessObject.id, 'DiagramBehavior__selected');
          }
        }
      });

      viewer.get('overlays').clear();
      if (data && endEvent) {
        this.addEndEventOverlay(endEvent);
      }
      if (data && hoveredNode && hoveredNode.$instanceOf('bpmn:EndEvent')) {
        this.addEndEventOverlay(hoveredNode);
      }

      if (gateway && endEvent) {
        const reachableNodes = getDiagramElementsBetween(gateway, endEvent, viewer);
        reachableNodes.forEach(id => canvas.addMarker(id, 'analysis-part-highlight'));
      }
    }

    return null;
  }

  componentDidMount() {
    const {viewer} = this.props;
    const elementRegistry = viewer.get('elementRegistry');
    const canvas = viewer.get('canvas');

    // remove existing selection markers and indicate selectable status for all flownodes
    elementRegistry.forEach(({businessObject}) => {
      if (this.isValidNode(businessObject)) {
        canvas.addMarker(businessObject.id, 'DiagramBehavior__clickable');

        const gfx = elementRegistry.getGraphics(businessObject.id).querySelector('.djs-outline');

        gfx.setAttribute('rx', '14px');
        gfx.setAttribute('ry', '14px');
      }
    });

    viewer.on('element.hover', this.hoverHandler);
    viewer.on('element.out', this.outHandler);
    viewer.on('element.click', this.clickHandler);
    this.props.setViewer(viewer);
  }

  componentWillUnmount() {
    const {viewer} = this.props;

    viewer.off('element.hover', this.hoverHandler);
    viewer.off('element.out', this.outHandler);
    viewer.off('element.click', this.clickHandler);

    viewer.get('overlays').clear();
    const elementRegistry = viewer.get('elementRegistry');
    const canvas = viewer.get('canvas');

    // remove existing selection markers
    elementRegistry.forEach(element => {
      canvas.removeMarker(element, 'analysis-part-highlight');

      const {businessObject} = element;
      if (this.isValidNode(businessObject)) {
        canvas.removeMarker(businessObject.id, 'DiagramBehavior__highlight');
        canvas.removeMarker(businessObject.id, 'DiagramBehavior__selected');
        canvas.removeMarker(businessObject.id, 'DiagramBehavior__clickable');
      }
    });
  }

  hoverHandler = ({element}) => {
    if (element.businessObject && this.isValidNode(element.businessObject)) {
      this.props.updateHover(element.businessObject);
    }
  };

  outHandler = ({element}) => {
    this.props.updateHover(null);
  };

  clickHandler = ({element: {businessObject}}) => {
    if (businessObject.$instanceOf('bpmn:Gateway')) {
      if (this.props.gateway === businessObject) {
        this.props.updateSelection('gateway', null);
      } else {
        this.props.updateSelection('gateway', businessObject);
      }
    } else if (businessObject.$instanceOf('bpmn:EndEvent')) {
      if (this.props.endEvent === businessObject) {
        this.props.updateSelection('endEvent', null);
      } else {
        this.props.updateSelection('endEvent', businessObject);
      }
    }
  };

  isValidNode(node) {
    return node.$instanceOf('bpmn:Gateway') || node.$instanceOf('bpmn:EndEvent');
  }

  addEndEventOverlay(element) {
    const {
      viewer,
      data: {
        result: {data, processInstanceCount: piCount}
      }
    } = this.props;

    const flowNodes = formatters.objectifyResult(data);

    const overlays = viewer.get('overlays');
    const value = flowNodes[element.id] || 0;

    // create overlay node from html string
    const container = document.createElement('div');
    const percentageValue = Math.round((value / piCount) * 1000) / 10 || 0;

    container.innerHTML = `<div class="DiagramBehavior__overlay" role="tooltip">
      <table class="DiagramBehavior__end-event-statistics">
        <tbody>
          <tr>
            <td>${piCount}</td>
            <td>${t('analysis.tooltip.totalInstances')}</td>
          </tr>
          <tr>
            <td>${value}</td>
            <td>${t('analysis.tooltip.reachedEnd')}</td>
          </tr>
          <tr>
            <td>${percentageValue}%</td>
            <td>${t('analysis.tooltip.reachedEndPercentage')}</td>
          </tr>
        </tbody>
      </table>
    </div>`;
    const overlayHtml = container.firstChild;

    // stop propagation of mouse event so that click+drag does not move canvas
    // when user tries to select text
    overlayHtml.addEventListener('mousedown', evt => {
      evt.stopPropagation();
    });

    // calculate overlay dimensions
    document.body.appendChild(overlayHtml);
    const overlayWidth = overlayHtml.clientWidth;
    const overlayHeight = overlayHtml.clientHeight;

    document.body.removeChild(overlayHtml);

    // calculate element dimensions
    const elementNode = viewer
      .get('elementRegistry')
      .getGraphics(element.id)
      .querySelector('.djs-hit');
    const elementWidth = parseInt(elementNode.getAttribute('width'), 10);
    const elementHeight = parseInt(elementNode.getAttribute('height'), 10);

    const position = {bottom: 0, right: 0};

    const viewbox = viewer.get('canvas').viewbox();
    const elementPosition = viewer.get('elementRegistry').get(element.id);

    if (elementPosition.x + elementWidth + overlayWidth > viewbox.x + viewbox.width) {
      position.right = undefined;
      position.left = -overlayWidth;
    }
    if (elementPosition.y + elementHeight + overlayHeight > viewbox.y + viewbox.height) {
      position.bottom = undefined;
      position.top = -overlayHeight;
    }

    // add overlay to viewer
    overlays.add(element.id, 'ANALYSIS_OVERLAY', {
      position,
      show: {
        minZoom: -Infinity,
        maxZoom: +Infinity
      },
      html: overlayHtml
    });
  }
}
