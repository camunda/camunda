/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

// @ts-expect-error ts-migrate(7016) FIXME: Try `npm install @types/bpmn-moddle` if it exists ... Remove this comment to see the full error message
import BpmnModdle from 'bpmn-moddle';

const moddle = new BpmnModdle();

/**
 * Utility that makes a call with a process xml for process nodes
 * @param {String} xml
 * @return: a Promise that when resolves returns the list of the process nodes
 */
async function parseDiagramXML(xml: any) {
  const {rootElement: definitions, elementsById: bpmnElements} =
    await moddle.fromXML(xml, 'bpmn:Definitions');

  return {
    definitions,
    bpmnElements,
  };
}

export {parseDiagramXML};
