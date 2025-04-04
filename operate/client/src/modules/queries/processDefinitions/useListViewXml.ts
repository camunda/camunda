/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ParsedXmlData,
  useProcessDefinitionXml,
} from './useProcessDefinitionXml';

type ExtendedParsedXmlData = ParsedXmlData & {
  flowNodeFilterOptions: Array<{id: string; label: string}>;
};

function listViewXmlXmlParser({
  xml,
  diagramModel,
  selectableFlowNodes,
}: ParsedXmlData) {
  return {
    xml,
    diagramModel,
    selectableFlowNodes: selectableFlowNodes,
    flowNodeFilterOptions: selectableFlowNodes
      .map(({id, name}) => ({
        id,
        label: name ?? id,
      }))
      .sort((node, nextNode) => {
        const label = node.label.toUpperCase();
        const nextLabel = nextNode.label.toUpperCase();

        if (label < nextLabel) {
          return -1;
        }
        if (label > nextLabel) {
          return 1;
        }

        return 0;
      }),
  };
}

function useListViewXml({
  processDefinitionKey,
}: {
  processDefinitionKey?: string;
}) {
  return useProcessDefinitionXml<ExtendedParsedXmlData>({
    processDefinitionKey,
    select: listViewXmlXmlParser,
    enabled: !!processDefinitionKey,
  });
}

export {useListViewXml};
