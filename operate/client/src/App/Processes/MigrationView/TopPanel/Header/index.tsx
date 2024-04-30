/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {PanelHeader} from 'modules/components/PanelHeader';
import {MigrationStep} from './styled';
import {CopiableProcessID} from 'App/Processes/CopiableProcessID';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processesStore} from 'modules/stores/processes/processes.migration';

const Header: React.FC = observer(() => {
  const {bpmnProcessId, processName} =
    processesStore.getSelectedProcessDetails();

  return (
    <PanelHeader title={processName}>
      <CopiableProcessID bpmnProcessId={bpmnProcessId} />
      <MigrationStep>
        {`Migration Step ${processInstanceMigrationStore.currentStep?.stepNumber} - ${processInstanceMigrationStore.currentStep?.stepDescription}`}
      </MigrationStep>
    </PanelHeader>
  );
});

export {Header};
