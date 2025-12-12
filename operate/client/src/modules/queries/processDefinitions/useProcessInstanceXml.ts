/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  type ParsedXmlData,
  useProcessDefinitionXml,
} from './useProcessDefinitionXml';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {businessObjectsParser} from './useBusinessObjects';

type ExtendedParsedXmlData = ParsedXmlData & {
  businessObjects: {[key: string]: BusinessObject};
};

function processInstanceXmlParser({
  xml,
  diagramModel,
  selectableFlowNodes,
}: ParsedXmlData) {
  const businessObjects = businessObjectsParser({
    diagramModel,
  });

  return {
    xml,
    diagramModel,
    selectableFlowNodes,
    businessObjects,
  };
}

function useProcessInstanceXml({
  processDefinitionKey,
}: {
  processDefinitionKey?: string;
}) {
  return useProcessDefinitionXml<ExtendedParsedXmlData>({
    processDefinitionKey,
    select: processInstanceXmlParser,
    enabled: !!processDefinitionKey,
  });
}

export {useProcessInstanceXml};
