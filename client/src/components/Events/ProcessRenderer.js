/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';

import mappedIcon from './icons/mapped.svg';

export default function ProcessRenderer({
  viewer,
  name = '',
  mappings = {},
  onChange = () => {},
  onSelectNode = () => {}
}) {
  function onChangeWithViewer() {
    viewer.saveXML((err, xml) => {
      if (err) {
        throw err;
      } else {
        onChange(viewer, xml);
      }
    });
  }

  useEffect(() => {
    const eventBus = viewer.get('eventBus');

    eventBus.on('commandStack.changed', onChangeWithViewer);
    eventBus.on('selection.changed', onSelectNode);

    const overlays = viewer.get('overlays');
    const elementRegistry = viewer.get('elementRegistry');

    function createOverlay(id, type) {
      const position = type === 'start' ? {top: -33, left: 9} : {top: -33, right: 27};
      overlays.add(id, 'MAPPED', {
        position,
        show: {
          minZoom: -Infinity,
          maxZoom: +Infinity
        },
        html: `<img src="${mappedIcon}" />`
      });
    }

    Object.entries(mappings).forEach(([id, {start, end}]) => {
      if (elementRegistry.get(id)) {
        if (start) {
          createOverlay(id, 'start');
        }
        if (end) {
          createOverlay(id, 'end');
        }
      }
    });

    return () => {
      eventBus.off('commandStack.changed', onChangeWithViewer);
      eventBus.off('selection.changed', onSelectNode);
      overlays.remove({type: 'MAPPED'});
    };
  });

  viewer.get('elementRegistry').forEach(({businessObject}) => {
    if (businessObject.$instanceOf('bpmn:Process')) {
      businessObject.name = name;
    }
  });

  return null;
}
