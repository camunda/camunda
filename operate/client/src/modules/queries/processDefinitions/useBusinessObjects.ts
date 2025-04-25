/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {
  ParsedXmlData,
  useProcessDefinitionXml,
} from './useProcessDefinitionXml';
import {isFlowNode} from 'modules/utils/flowNodes';
import {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';

function businessObjectsParser({diagramModel}: ParsedXmlData): BusinessObjects {
  const businessObjects = Object.entries(diagramModel.elementsById).reduce(
    (flowNodes, [flowNodeId, businessObject]) => {
      if (isFlowNode(businessObject)) {
        return {...flowNodes, [flowNodeId]: businessObject};
      } else {
        return flowNodes;
      }
    },
    {},
  );

  return businessObjects;
}

const useBusinessObjects = () => {
  const processDefinitionKey = useProcessDefinitionKeyContext();

  return useProcessDefinitionXml<BusinessObjects>({
    processDefinitionKey,
    select: businessObjectsParser,
    enabled: !!processDefinitionKey,
  });
};

export {businessObjectsParser, useBusinessObjects};
