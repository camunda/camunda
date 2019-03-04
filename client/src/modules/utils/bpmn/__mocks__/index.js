/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {createDiagramNodes, createDefinitions} from 'modules/testUtils';

const bpmnElements = createDiagramNodes();

export const parsedDiagram = {bpmnElements, definitions: createDefinitions()};

export const parseDiagramXML = jest.fn(async xml => {
  return {bpmnElements, definitions: createDefinitions()};
});
