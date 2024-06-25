/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {Toggle} from '@carbon/react';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {executionCountToggleStore} from 'modules/stores/executionCountToggle';
import {useEffect} from 'react';

const ExecutionCountToggle: React.FC = observer(() => {
  const {status: diagramStatus} = processInstanceDetailsDiagramStore.state;
  const {
    state: {isExecutionCountVisible},
    toggleExecutionCountVisibility,
  } = executionCountToggleStore;

  useEffect(() => executionCountToggleStore.reset, []);

  const isDisabled = diagramStatus !== 'fetched';
  return (
    <Toggle
      aria-label={`${isExecutionCountVisible ? 'Hide' : 'Show'} Execution Count`}
      id="toggle-execution-count"
      labelA="Show Execution Count"
      labelB="Hide Execution Count"
      onClick={toggleExecutionCountVisibility}
      disabled={isDisabled}
      size="sm"
      toggled={isExecutionCountVisible}
    />
  );
});

export {ExecutionCountToggle};
