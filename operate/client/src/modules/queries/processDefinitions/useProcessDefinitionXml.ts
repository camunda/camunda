/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {fetchProcessDefinitionXml} from 'modules/api/v2/processDefinitions/fetchProcessDefinitionXml';
import {skipToken, useQuery} from '@tanstack/react-query';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {getFlowNodes} from 'modules/utils/flowNodes';
import type {DiagramModel} from 'bpmn-moddle';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import type {ProcessDefinition} from '@vzeta/camunda-api-zod-schemas/8.8';

const PROCESS_DEFINITION_XML_QUERY_KEY = 'processDefinitionXml';

type ParsedXmlData = {
  xml: string;
  diagramModel: DiagramModel;
  selectableFlowNodes: BusinessObject[];
};

async function processDefinitionParser(data: string): Promise<ParsedXmlData> {
  const diagramModel = await parseDiagramXML(data);
  const selectableFlowNodes = getFlowNodes(diagramModel?.elementsById);

  return {xml: data, diagramModel, selectableFlowNodes};
}

function useProcessDefinitionXml<T = ParsedXmlData>({
  processDefinitionKey,
  select,
  enabled = true,
}: {
  processDefinitionKey?: ProcessDefinition['processDefinitionKey'];
  select?: (data: ParsedXmlData) => T;
  enabled?: boolean;
}) {
  return useQuery({
    queryKey: [PROCESS_DEFINITION_XML_QUERY_KEY, processDefinitionKey],
    queryFn:
      enabled && !!processDefinitionKey
        ? async () => {
            const {response, error} =
              await fetchProcessDefinitionXml(processDefinitionKey);

            if (response !== null) {
              return processDefinitionParser(response);
            }

            throw error;
          }
        : skipToken,
    select,
    staleTime: Infinity,
  });
}

export {useProcessDefinitionXml};
export type {ParsedXmlData};
