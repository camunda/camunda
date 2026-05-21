/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {TableBatchAction} from '@carbon/react';
import {MigrateAlt} from '@carbon/react/icons';
import {processInstancesSelectionStore} from 'modules/stores/instancesSelection';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import {MigrationHelperModal} from 'modules/components/HelperModal/MigrationHelperModal';
import {tracking} from 'modules/tracking';
import {batchModificationStore} from 'modules/stores/batchModification';
import {getStateLocally} from 'modules/utils/localStorage';
import {useListViewXml} from 'modules/queries/processDefinitions/useListViewXml';
import {useSelectedProcessDefinitionContext} from '../../../selectedProcessDefinitionContext';
import {variableFilterStore} from 'modules/stores/variableFilter';

const MigrateAction: React.FC = observer(() => {
  const {
    selectedIds,
    hasSelectedRunningInstances,
    excludedIds,
    state: {selectionMode},
  } = processInstancesSelectionStore;

  const selectedProcessDefinition = useSelectedProcessDefinitionContext();
  const processDefinitionXml = useListViewXml({
    processDefinitionKey: selectedProcessDefinition?.processDefinitionKey,
  });
  const hasXmlError = processDefinitionXml?.isError;

  const isDisabled =
    batchModificationStore.state.isEnabled ||
    !selectedProcessDefinition ||
    !hasSelectedRunningInstances ||
    hasXmlError;

  const getTooltipText = () => {
    if (!selectedProcessDefinition) {
      return 'To start the migration process, choose a process and version first.';
    }

    if (hasXmlError) {
      return 'Issue fetching diagram, contact admin if problem persists.';
    }

    if (!hasSelectedRunningInstances) {
      return 'You can only migrate instances in active or incident state.';
    }
    return undefined;
  };

  const handleSubmit = () => {
    if (!selectedProcessDefinition) {
      return;
    }

    processInstanceMigrationStore.setSourceProcessDefinition(
      selectedProcessDefinition,
    );

    processInstanceMigrationStore.setSelectedInstancesCount(
      processInstancesSelectionStore.selectedCount,
    );
    processInstanceMigrationStore.setBatchOperationQuery({
      variable: variableFilterStore.variableWithValidatedValues,
      ids: selectionMode === 'INCLUDE' ? selectedIds : [],
      excludeIds: selectionMode === 'EXCLUDE' ? excludedIds : [],
    });
    processInstanceMigrationStore.enable();

    tracking.track({
      eventName: 'process-instance-migration-mode-entered',
    });
  };

  return (
    <ModalStateManager
      renderLauncher={({setOpen}) => (
        <TableBatchAction
          renderIcon={MigrateAlt}
          onClick={() => {
            if (getStateLocally()?.hideMigrationHelperModal) {
              handleSubmit();
            } else {
              setOpen(true);
            }
            tracking.track({
              eventName: 'process-instance-migration-button-clicked',
            });
          }}
          disabled={isDisabled}
          title={
            batchModificationStore.state.isEnabled
              ? 'Not available in batch modification mode'
              : getTooltipText()
          }
        >
          Migrate
        </TableBatchAction>
      )}
    >
      {({open, setOpen}) => (
        <MigrationHelperModal
          open={open}
          onClose={() => setOpen(false)}
          onSubmit={handleSubmit}
        />
      )}
    </ModalStateManager>
  );
});

export {MigrateAction};
