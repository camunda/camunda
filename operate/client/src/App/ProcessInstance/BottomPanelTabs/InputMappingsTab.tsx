/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {InputOutputMappings} from './InputOutputMappings';

const InputMappingsTab: React.FC = () => {
  const {selectedElementId} = useProcessInstanceElementSelection();

  if (selectedElementId === null) {
    return null;
  }

  return <InputOutputMappings type="Input" elementId={selectedElementId} />;
};

export {InputMappingsTab};
