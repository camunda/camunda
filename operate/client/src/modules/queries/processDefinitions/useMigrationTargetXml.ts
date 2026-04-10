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
import {isMigratableElement} from 'modules/bpmn-js/utils/isMigratableElement';
import {hasParentProcess} from 'modules/bpmn-js/utils/hasParentProcess';
import {getMappableSequenceFlows} from 'modules/utils/sequenceFlows';
import {useMemo} from 'react';

const getMigrationTargetXmlParser =
  (targetProcessDefinitionId?: string) =>
  ({
    xml,
    diagramModel,
    selectableElements,
  }: {
    xml: string;
    diagramModel: DiagramModel;
    selectableElements: BusinessObject[];
  }) => {
    return {
      xml,
      diagramModel,
      selectableElements: selectableElements
        .filter(isMigratableElement)
        .filter((targetElement) => {
          return (
            targetProcessDefinitionId !== undefined &&
            hasParentProcess({
              element: diagramModel?.elementsById[targetElement.id],
              bpmnProcessId: targetProcessDefinitionId,
            })
          );
        })
        .map((element) => {
          return {...element, name: element.name ?? element.id};
        }),
      selectableSequenceFlows: getMappableSequenceFlows(
        diagramModel?.elementsById,
      ),
    };
  };

function useMigrationTargetXml({
  processDefinitionKey,
  processDefinitionId: processDefinitionId,
}: {
  processDefinitionKey?: string;
  processDefinitionId?: string;
}) {
  const select = useMemo(
    () => getMigrationTargetXmlParser(processDefinitionId),
    [processDefinitionId],
  );

  return useProcessDefinitionXml({
    processDefinitionKey,
    select,
  });
}

export {useMigrationTargetXml};
