/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createDefinitions} from 'modules/testUtils';
import {createDiagramNodes} from './bpmn.setup';

const bpmnElements = createDiagramNodes();

export const parsedDiagram = {bpmnElements, definitions: createDefinitions()};

export const parseDiagramXML = jest.fn(async (xml) => {
  return {bpmnElements, definitions: createDefinitions()};
});
