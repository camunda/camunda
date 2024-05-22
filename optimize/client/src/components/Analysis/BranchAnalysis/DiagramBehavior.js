/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect} from 'react';

import {ClickBehavior, PartHighlight} from 'components';
import {formatters, getReportResult} from 'services';
import {t} from 'translation';

import './DiagramBehavior.scss';

export default function DiagramBehavior({
  setViewer,
  viewer,
  hoveredNode,
  updateHover,
  updateSelection,
  data,
  gateway,
  endEvent,
}) {
  useEffect(() => {
    setViewer(viewer);
    return () => viewer.get('overlays').clear();
  }, [setViewer, viewer]);

  // clear previously highlighted nodes
  viewer.get('overlays').clear();

  if (data && endEvent) {
    addEndEventOverlay(endEvent, viewer, data);
  }
  if (data && hoveredNode && hoveredNode.$instanceOf('bpmn:EndEvent')) {
    addEndEventOverlay(hoveredNode, viewer, data);
  }

  const selectedNodes = [];
  if (gateway) {
    selectedNodes.push(gateway);
  }
  if (endEvent) {
    selectedNodes.push(endEvent);
  }

  return (
    <>
      <ClickBehavior
        viewer={viewer}
        onClick={(node) => {
          const type = node.$instanceOf('bpmn:Gateway') ? 'gateway' : 'endEvent';
          if (node === gateway || node === endEvent) {
            return updateSelection(type, null);
          }
          updateSelection(type, node);
        }}
        selectedNodes={selectedNodes}
        onHover={(hoveredNode) => updateHover(hoveredNode)}
        nodeTypes={['Gateway', 'EndEvent']}
      />
      <PartHighlight viewer={viewer} nodes={selectedNodes} />
    </>
  );
}

function addEndEventOverlay(element, viewer, report) {
  const {data, instanceCount: piCount} = getReportResult(report);

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
  overlayHtml.addEventListener('mousedown', (evt) => {
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
      maxZoom: +Infinity,
    },
    html: overlayHtml,
  });
}
