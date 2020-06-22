/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import bpmnJs from 'bpmn-js';

/**
 * Utility that makes a call with a workflow xml for worklow nodes
 * @param {String} xml
 * @return: a Promise that when resolves returns the list of the workflow nodes
 */
export function parseDiagramXML(xml) {
  bpmnJs.prototype.options = {};
  const moddle = bpmnJs.prototype._createModdle({});
  return moddle.fromXML(xml, 'bpmn:Definitions').then((bpmn) => {
    return {
      definitions: bpmn.rootElement,
      bpmnElements: bpmn.elementsById,
    };
  });
}
