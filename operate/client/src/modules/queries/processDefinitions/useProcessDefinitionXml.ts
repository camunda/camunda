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
import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.8';
import {isRequestError} from 'modules/request';
import {HTTP_STATUS_FORBIDDEN} from 'modules/constants/statusCode';

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

const getUseProcessDefinitionXmlOptions = (
  processDefinitionKey?: ProcessDefinition['processDefinitionKey'],
) => {
  return {
    queryKey: [PROCESS_DEFINITION_XML_QUERY_KEY, processDefinitionKey],
    queryFn:
      processDefinitionKey === undefined
        ? skipToken
        : async () => {
            const {response, error} =
              await fetchProcessDefinitionXml(processDefinitionKey);

            if (response !== null) {
              return processDefinitionParser(response);
            }

            throw error;
          },
  } as const;
};

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
    ...getUseProcessDefinitionXmlOptions(processDefinitionKey),
    enabled,
    select,
    staleTime: 'static',
    refetchOnWindowFocus: false,
    retryOnMount: false,
    refetchOnMount: (query) => {
      const lastError = query.state.error;
      return (
        isRequestError(lastError) &&
        lastError?.response?.status !== HTTP_STATUS_FORBIDDEN
      );
    },
    refetchOnReconnect: (query) => {
      const lastError = query.state.error;
      return (
        isRequestError(lastError) &&
        lastError?.response?.status !== HTTP_STATUS_FORBIDDEN
      );
    },
  });
}

export {useProcessDefinitionXml, getUseProcessDefinitionXmlOptions};
export type {ParsedXmlData};
