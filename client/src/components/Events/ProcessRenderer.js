/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useEffect} from 'react';

export default function ProcessRenderer({viewer, name, getXml, onChange}) {
  useEffect(() => {
    viewer.get('eventBus').on('commandStack.changed', onChange);
    return () => viewer.get('eventBus').off('commandStack.changed', onChange);
  });

  viewer.get('elementRegistry').forEach(({businessObject}) => {
    if (businessObject.$instanceOf('bpmn:Process')) {
      businessObject.name = name;
    }
  });

  getXml.action = () =>
    new Promise((resolve, reject) => {
      viewer.saveXML((err, xml) => {
        if (err) {
          reject(err);
        } else {
          resolve(xml);
        }
      });
    });

  return null;
}
