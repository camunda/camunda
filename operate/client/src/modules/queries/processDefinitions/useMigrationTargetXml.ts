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
import {isMigratableFlowNode} from 'modules/stores/processXml/utils/isMigratableFlowNode';
import {hasParentProcess} from 'modules/bpmn-js/utils/hasParentProcess';
import {processesStore} from 'modules/stores/processes/processes.migration';

function migrationTargetXmlParser({
  xml,
  diagramModel,
  selectableFlowNodes,
}: {
  xml: string;
  diagramModel: DiagramModel;
  selectableFlowNodes: BusinessObject[];
}) {
  return {
    xml,
    diagramModel,
    selectableFlowNodes: selectableFlowNodes
      .filter(isMigratableFlowNode)
      .filter((targetFlowNode) => {
        const targetBpmnProcessId =
          processesStore.migrationState.selectedTargetProcess?.bpmnProcessId;

        return (
          targetBpmnProcessId !== undefined &&
          hasParentProcess({
            flowNode: diagramModel?.elementsById[targetFlowNode.id],
            bpmnProcessId: targetBpmnProcessId,
          })
        );
      })
      .map((flowNode) => {
        return {...flowNode, name: flowNode.name ?? flowNode.id};
      }),
  };
}

function useMigrationTargetXml({
  processDefinitionKey,
}: {
  processDefinitionKey?: string;
}) {
  return useProcessDefinitionXml({
    processDefinitionKey,
    select: migrationTargetXmlParser,
    enabled: !!processDefinitionKey,
  });
}

export {useMigrationTargetXml};
