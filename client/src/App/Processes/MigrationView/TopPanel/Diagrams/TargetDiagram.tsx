/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {Header} from './Header';
import {DiagramWrapper} from './styled';
import {observer} from 'mobx-react';
import {TargetProcessField} from './TargetProcessField';
import {TargetVersionField} from './TargetVersionField';
import {processesStore} from 'modules/stores/processes/processes.migration';

const TargetDiagram: React.FC = observer(() => {
  const {
    migrationState: {selectedTargetProcess, selectedTargetVersion},
  } = processesStore;

  return (
    <DiagramWrapper>
      <Header
        mode={processInstanceMigrationStore.isSummaryStep ? 'view' : 'edit'}
        label="Target"
        processName={selectedTargetProcess?.name ?? ''}
        processVersion={selectedTargetVersion?.toString() ?? ''}
      >
        <TargetProcessField />
        <TargetVersionField />
      </Header>
      Target Diagram
    </DiagramWrapper>
  );
});

export {TargetDiagram};
