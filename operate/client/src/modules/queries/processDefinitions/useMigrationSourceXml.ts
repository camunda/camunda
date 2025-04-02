/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {useProcessDefinitionXml} from './useProcessDefinitionXml';
import {DiagramModel} from 'bpmn-moddle';
import {isMigratableFlowNode} from 'modules/bpmn-js/utils/isMigratableFlowNode';
import {hasParentProcess} from 'modules/bpmn-js/utils/hasParentProcess';

const getMigrationSourceXmlParser =
  (sourceBpmnProcessId?: string) =>
  ({
    xml,
    diagramModel,
    selectableFlowNodes,
  }: {
    xml: string;
    diagramModel: DiagramModel;
    selectableFlowNodes: BusinessObject[];
  }) => {
    return {
      xml,
      diagramModel,
      selectableFlowNodes: selectableFlowNodes
        .filter(isMigratableFlowNode)
        .filter((sourceFlowNode) => {
          return (
            sourceBpmnProcessId !== undefined &&
            hasParentProcess({
              flowNode: diagramModel?.elementsById[sourceFlowNode.id],
              bpmnProcessId: sourceBpmnProcessId,
            })
          );
        })
        .map((flowNode) => {
          return {...flowNode, name: flowNode.name ?? flowNode.id};
        }),
    };
  };

function useMigrationSourceXml({
  processDefinitionKey,
  bpmnProcessId,
}: {
  processDefinitionKey?: string;
  bpmnProcessId?: string;
}) {
  return useProcessDefinitionXml({
    processDefinitionKey,
    select: getMigrationSourceXmlParser(bpmnProcessId),
    enabled: !!bpmnProcessId,
  });
}

export {useMigrationSourceXml};
