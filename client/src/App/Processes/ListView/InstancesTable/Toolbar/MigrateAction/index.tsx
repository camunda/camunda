/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {useLocation} from 'react-router-dom';
import {TableBatchAction} from '@carbon/react';
import {MigrateAlt} from '@carbon/react/icons';
import {Restricted} from 'modules/components/Restricted';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {processInstancesStore} from 'modules/stores/processInstances';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processXmlStore as processXmlMigrationSourceStore} from 'modules/stores/processXml/processXml.migration.source';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {processesStore} from 'modules/stores/processes/processes.list';

const MigrateAction: React.FC = observer(() => {
  const location = useLocation();
  const {version, process, tenant} = getProcessInstanceFilters(location.search);

  const isVersionSelected = version !== undefined && version !== 'all';
  const hasSelectedFinishedInstances =
    processInstancesSelectionStore.state.selectionMode === 'ALL' ||
    processInstancesStore.state.processInstances.some((processInstance) => {
      return (
        processInstancesSelectionStore.selectedProcessInstanceIds.includes(
          processInstance.id,
        ) && ['ACTIVE', 'INCIDENT'].includes(processInstance.state)
      );
    });

  const isDisabled = !isVersionSelected || !hasSelectedFinishedInstances;

  const getTooltipText = () => {
    if (!isVersionSelected) {
      return 'To start the migration process, choose a process and version first.';
    }

    if (!hasSelectedFinishedInstances) {
      return 'You can only migrate instances in active or incident state.';
    }
  };

  return (
    <Restricted
      scopes={['write']}
      resourceBasedRestrictions={{
        scopes: ['UPDATE_PROCESS_INSTANCE'],
        permissions: processesStore.getPermissions(process, tenant),
      }}
    >
      <TableBatchAction
        renderIcon={MigrateAlt}
        onClick={() => {
          processXmlMigrationSourceStore.setProcessXml(
            processXmlStore.state.xml,
          );
          processInstanceMigrationStore.enable();
        }}
        disabled={isDisabled}
        title={getTooltipText()}
      >
        Migrate
      </TableBatchAction>
    </Restricted>
  );
});

export {MigrateAction};
