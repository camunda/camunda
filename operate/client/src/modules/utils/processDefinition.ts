/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const getFullyQualifiedProcessDefinitionName = (processName: string, processVersion: any) => {
  return `${processName} v${processVersion}`;
};

const getFullyQualifiedProcessDefinitionNameBy = (definition: any) => {
  if(!definition) return undefined;

  // for now the definition can be of different types, so we try to get the name and version from different properties
  // later we should standardize this when we refactor the types
  const processName = definition.name || definition.processName || definition.processDefinitionId || definition.bpmnProcessId || definition.processDefinitionName || definition.processDefinitionId;
  const processVersion = definition.version || definition.processDefinitionVersion;

  return getFullyQualifiedProcessDefinitionName(processName, processVersion);
};

export {getFullyQualifiedProcessDefinitionName, getFullyQualifiedProcessDefinitionNameBy};
