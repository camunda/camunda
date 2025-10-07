/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessDefinition} from '@camunda/camunda-api-zod-schemas/8.8';
import _ from 'lodash';

const getFullyQualifiedProcessDefinitionName = (
  processName: string,
  processVersion: string,
) => {
  return `${_.kebabCase(processName)}_v${processVersion}`;
};

const getDiagramNameByProcessDefinition = (definition?: ProcessDefinition) => {
  if (!definition) {
    return getFullyQualifiedProcessDefinitionName('diagram', '0');
  }

  const processName = definition.name || definition.processDefinitionId;
  const processVersion = definition.version;

  return getFullyQualifiedProcessDefinitionName(
    processName,
    processVersion.toString(),
  );
};

export {getDiagramNameByProcessDefinition};
