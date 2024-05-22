/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect} from 'react';
import deepEqual from 'fast-deep-equal';
import ReactDOM from 'react-dom';
import Checkmark from './Checkmark';

const asMapping = ({group, source, eventName, eventLabel}) => ({
  group,
  source,
  eventName,
  eventLabel,
});

export default function ProcessRenderer({
  viewer,
  name = '',
  mappings = {},
  onChange = () => {},
  onSelectNode = () => {},
  selectedEvent,
}) {
  let isRemoveEvent = false;

  async function onChangeWithViewer() {
    const {xml} = await viewer.saveXML();
    onChange(xml, isRemoveEvent);
    isRemoveEvent = false;
  }

  function onElementRemove() {
    isRemoveEvent = true;
  }

  async function onSelectionChange(change) {
    // ensure that the new xml is loaded before changing the selected node
    await onChangeWithViewer();
    onSelectNode(change);
  }

  useEffect(() => {
    const eventBus = viewer.get('eventBus');

    eventBus.on('commandStack.changed', onChangeWithViewer);
    eventBus.on('shape.remove', onElementRemove);
    eventBus.on('selection.changed', onSelectionChange);

    const overlays = viewer.get('overlays');
    const elementRegistry = viewer.get('elementRegistry');

    function createOverlay(id, type, event) {
      const position = type === 'start' ? {top: -33, left: 9} : {top: -33, right: 27};
      const overlayHtml = document.createElement('div');
      ReactDOM.render(<Checkmark event={event} />, overlayHtml, () => {
        overlays.add(id, 'MAPPED', {
          position,
          show: {
            minZoom: -Infinity,
            maxZoom: +Infinity,
          },
          html: overlayHtml,
        });
      });
    }

    Object.entries(mappings).forEach(([id, {start, end}]) => {
      if (elementRegistry.get(id)) {
        if (start) {
          createOverlay(id, 'start', start);
        }
        if (end) {
          createOverlay(id, 'end', end);
        }
      }
    });

    return () => {
      eventBus.off('commandStack.changed', onChangeWithViewer);
      eventBus.off('selection.changed', onSelectNode);
      eventBus.off('shape.remove', onElementRemove);
      overlays.remove({type: 'MAPPED'});
    };
  });

  useEffect(() => {
    if (selectedEvent) {
      const selected = findSelectedNode(viewer, mappings, selectedEvent);
      viewer.get('zoomScroll').reset();
      viewer.get('selection').select(selected);
      centerElement(viewer, selected);
      onSelectNode({newSelection: [selected]});
    }
  }, [mappings, selectedEvent, viewer, onSelectNode]);

  viewer.get('elementRegistry').forEach(({businessObject}) => {
    if (businessObject.$instanceOf('bpmn:Process')) {
      businessObject.name = name;
    }
  });

  return null;
}

function findSelectedNode(viewer, mappings, event) {
  let selected;
  viewer.get('elementRegistry').forEach((element) => {
    const {start, end} = mappings[element.id] || {};
    if (deepEqual(start, asMapping(event)) || deepEqual(end, asMapping(event))) {
      selected = element;
    }
  });
  return selected;
}

function centerElement(viewer, el) {
  // assuming we center on a shape.
  // for connections we must compute the bounding box
  // based on the connection's waypoints
  const canvas = viewer.get('canvas');
  var currentViewbox = canvas.viewbox();

  var elementMid = {
    x: el.x + el.width / 2,
    y: el.y + el.height / 2,
  };

  canvas.viewbox({
    x: elementMid.x - currentViewbox.width / 2,
    y: elementMid.y - currentViewbox.height / 2,
    width: currentViewbox.width,
    height: currentViewbox.height,
  });
}
