/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  fetchProcessDefinitionXml,
  ProcessDefinitionKey,
} from 'modules/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {genericQueryOptions} from '../genericQuery';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {getFlowNodes} from 'modules/utils/flowNodes';
import {useQuery} from '@tanstack/react-query';

function getQueryKey(processDefinitionKey: ProcessDefinitionKey) {
  return ['processDefinitionXml', processDefinitionKey];
}

async function processDefinitionParser(data: string) {
  const diagramModel = await parseDiagramXML(data);
  const selectableFlowNodes = getFlowNodes(diagramModel?.elementsById);

  return {xml: data, diagramModel, selectableFlowNodes};
}

function useProcessDefinitionXml({
  processDefinitionKey,
  enabled,
}: {
  processDefinitionKey: ProcessDefinitionKey;
  enabled?: boolean;
}) {
  const queryKey = getQueryKey(processDefinitionKey);

  const queryResults = useQuery(
    genericQueryOptions(
      queryKey,
      () => fetchProcessDefinitionXml(processDefinitionKey),
      {
        queryKey,
        enabled,
        select: processDefinitionParser,
      },
    ),
  );

  return queryResults;
}

export {useProcessDefinitionXml};
