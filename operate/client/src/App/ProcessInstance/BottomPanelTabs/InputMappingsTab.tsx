/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Navigate, useLocation} from 'react-router-dom';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {useProcessInstancePageParams} from '../useProcessInstancePageParams';
import {Paths} from 'modules/Routes';
import {InputOutputMappings} from './InputOutputMappings';

const InputMappingsTab: React.FC = () => {
  const {selectedElementId} = useProcessInstanceElementSelection();
  const {processInstanceId} = useProcessInstancePageParams();
  const location = useLocation();

  if (selectedElementId === null) {
    return (
      <Navigate
        to={{
          ...location,
          pathname: Paths.processInstanceVariables({processInstanceId}),
        }}
        replace
      />
    );
  }

  return <InputOutputMappings type="Input" elementId={selectedElementId} />;
};

export {InputMappingsTab};
