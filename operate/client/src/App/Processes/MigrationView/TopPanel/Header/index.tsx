/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
