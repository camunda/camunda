/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import BpmnModdle from 'bpmn-moddle';

const moddle = new BpmnModdle();

/**
 * Utility that makes a call with a workflow xml for worklow nodes
 * @param {String} xml
 * @return: a Promise that when resolves returns the list of the workflow nodes
 */
async function parseDiagramXML(xml) {
  const {
    rootElement: definitions,
    elementsById: bpmnElements,
  } = await moddle.fromXML(xml, 'bpmn:Definitions');

  return {
    definitions,
    bpmnElements,
  };
}

export {parseDiagramXML};
