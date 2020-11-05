/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

// @ts-expect-error ts-migrate(7016) FIXME: Try `npm install @types/bpmn-moddle` if it exists ... Remove this comment to see the full error message
import BpmnModdle from 'bpmn-moddle';

const moddle = new BpmnModdle();

/**
 * Utility that makes a call with a workflow xml for workflow nodes
 * @param {String} xml
 * @return: a Promise that when resolves returns the list of the workflow nodes
 */
async function parseDiagramXML(xml: any) {
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
