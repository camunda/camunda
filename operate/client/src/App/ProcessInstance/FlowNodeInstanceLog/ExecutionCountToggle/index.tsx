/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {Toggle} from '@carbon/react';
import {executionCountToggleStore} from 'modules/stores/executionCountToggle';
import {useEffect} from 'react';
import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';

const ExecutionCountToggle: React.FC = observer(() => {
  const {
    state: {isExecutionCountVisible},
    toggleExecutionCountVisibility,
  } = executionCountToggleStore;

  useEffect(() => executionCountToggleStore.reset, []);

  const processDefinitionKey = useProcessDefinitionKeyContext();
  const {isSuccess} = useProcessInstanceXml({processDefinitionKey});

  return (
    <Toggle
      aria-label={`${isExecutionCountVisible ? 'Hide' : 'Show'} Execution Count`}
      id="toggle-execution-count"
      labelA="Show Execution Count"
      labelB="Hide Execution Count"
      onClick={toggleExecutionCountVisibility}
      disabled={!isSuccess}
      size="sm"
      toggled={isExecutionCountVisible}
    />
  );
});

export {ExecutionCountToggle};
