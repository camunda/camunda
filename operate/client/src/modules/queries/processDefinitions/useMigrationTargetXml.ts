/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {useProcessDefinitionXml} from './useProcessDefinitionXml';
import type {DiagramModel} from 'bpmn-moddle';
import {isMigratableFlowNode} from 'modules/bpmn-js/utils/isMigratableFlowNode';
import {hasParentProcess} from 'modules/bpmn-js/utils/hasParentProcess';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {getMappableSequenceFlows} from 'modules/utils/sequenceFlows';

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
    selectableSequenceFlows: getMappableSequenceFlows(
      diagramModel?.elementsById,
    ),
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
  });
}

export {useMigrationTargetXml};
