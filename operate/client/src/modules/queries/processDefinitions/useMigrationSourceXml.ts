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

const getMigrationSourceXmlParser =
  (sourceProcessDefinitionId?: string) =>
  ({
    xml,
    diagramModel,
    selectableElements,
  }: {
    xml: string;
    diagramModel: DiagramModel;
    selectableElements: BusinessObject[];
  }) => {
    const selectableSequenceFlows = getMappableSequenceFlows(
      diagramModel?.elementsById,
    );

    return {
      xml,
      diagramModel,
      selectableElements: selectableElements
        .filter(isMigratableElement)
        .filter((sourceElement) => {
          return (
            sourceProcessDefinitionId !== undefined &&
            hasParentProcess({
              element: diagramModel?.elementsById[sourceElement.id],
              bpmnProcessId: sourceProcessDefinitionId,
            })
          );
        })
        .map((element) => {
          return {...element, name: element.name ?? element.id};
        }),
      selectableSequenceFlows,
    };
  };

function useMigrationSourceXml({
  processDefinitionKey,
  processDefinitionId,
}: {
  processDefinitionKey?: string;
  processDefinitionId?: string;
}) {
  const select = useMemo(
    () => getMigrationSourceXmlParser(processDefinitionId),
    [processDefinitionId],
  );

  return useProcessDefinitionXml({
    processDefinitionKey,
    select,
    enabled: !!processDefinitionId,
  });
}

export {useMigrationSourceXml};
