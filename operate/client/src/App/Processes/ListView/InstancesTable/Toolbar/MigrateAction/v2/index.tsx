/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {useLocation} from 'react-router-dom';
import {Link, OrderedList, Stack, TableBatchAction} from '@carbon/react';
import {MigrateAlt} from '@carbon/react/icons';
import {Restricted} from 'modules/components/Restricted';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processXmlStore as processXmlMigrationSourceStore} from 'modules/stores/processXml/processXml.migration.source';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {processesStore} from 'modules/stores/processes/processes.list';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import {ListItem} from '../styled';
import {tracking} from 'modules/tracking';
import {batchModificationStore} from 'modules/stores/batchModification';
import {HelperModal} from 'modules/components/HelperModal';
import {useProcessInstanceFilters} from 'modules/hooks/useProcessInstancesFilters';
import {getStateLocally} from 'modules/utils/localStorage';
import {usePrefetchProcessInstancesStatistics} from 'modules/queries/processInstancesStatistics/useProcessInstancesStatistics';
import {useCallback, useMemo} from 'react';

const localStorageKey = 'hideMigrationHelperModal';

const useMigrateAction = () => {
  const location = useLocation();
  const {version, process, tenant} = getProcessInstanceFilters(location.search);
  const {
    selectedProcessInstanceIds,
    hasSelectedRunningInstances,
    state: {isAllChecked},
  } = processInstancesSelectionStore;

  const isVersionSelected = version !== undefined && version !== 'all';
  const processInstanceFilters = useProcessInstanceFilters();
  const hasXmlError = processXmlStore.state.status === 'error';

  const isDisabled =
    batchModificationStore.state.isEnabled ||
    !isVersionSelected ||
    !hasSelectedRunningInstances ||
    hasXmlError;

  const requestFilterParameters = useMemo(
    () => ({
      ...processInstanceFilters,
      processInstanceKey: {
        $in: isAllChecked ? [] : selectedProcessInstanceIds,
        $nin: isAllChecked ? selectedProcessInstanceIds : [],
      },
    }),
    [isAllChecked, selectedProcessInstanceIds, processInstanceFilters],
  );

  usePrefetchProcessInstancesStatistics(requestFilterParameters);

  const handleSubmit = useCallback(() => {
    processXmlMigrationSourceStore.setProcessXml(processXmlStore.state.xml);

    processInstanceMigrationStore.setSelectedInstancesCount(
      processInstancesSelectionStore.selectedProcessInstanceCount,
    );
    processInstanceMigrationStore.setBatchOperationQuery(
      requestFilterParameters,
    );
    processInstanceMigrationStore.enable();

    tracking.track({
      eventName: 'process-instance-migration-mode-entered',
    });
  }, [requestFilterParameters]);

  return {
    isDisabled,
    handleSubmit,
    isVersionSelected,
    hasXmlError,
    hasSelectedRunningInstances,
    process,
    tenant,
  };
};

const MigrateAction: React.FC = observer(() => {
  const {
    isDisabled,
    handleSubmit,
    isVersionSelected,
    hasXmlError,
    hasSelectedRunningInstances,
    process,
    tenant,
  } = useMigrateAction();

  const getTooltipText = () => {
    if (!isVersionSelected) {
      return 'To start the migration process, choose a process and version first.';
    }

    if (hasXmlError) {
      return 'Issue fetching diagram, contact admin if problem persists.';
    }

    if (!hasSelectedRunningInstances) {
      return 'You can only migrate instances in active or incident state.';
    }
  };

  return (
    <Restricted
      resourceBasedRestrictions={{
        scopes: ['UPDATE_PROCESS_INSTANCE'],
        permissions: processesStore.getPermissions(process, tenant),
      }}
    >
      <ModalStateManager
        renderLauncher={({setOpen}) => (
          <TableBatchAction
            renderIcon={MigrateAlt}
            onClick={() => {
              if (getStateLocally()?.[localStorageKey]) {
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
          <HelperModal
            title="Migrate process instance versions"
            open={open}
            onClose={() => setOpen(false)}
            localStorageKey={localStorageKey}
            onSubmit={handleSubmit}
          >
            <Stack as={OrderedList} nested gap={5}>
              <ListItem>
                Migrate is used to migrate running process instances to a
                different process definition.
              </ListItem>
              <ListItem>
                When the migration steps are executed, all selected process
                instances will be affected. This can lead to interruptions,
                delays or changes.
              </ListItem>
              <ListItem>
                To minimize interruptions or delays, plan the migration at times
                when the system load is low.
              </ListItem>
            </Stack>
            <p>
              Questions or concerns? Check our{' '}
              <Link
                href="https://docs.camunda.io/docs/components/operate/userguide/process-instance-migration/"
                target="_blank"
                inline
              >
                migration documentation
              </Link>{' '}
              for guidance and best practices.
            </p>
          </HelperModal>
        )}
      </ModalStateManager>
    </Restricted>
  );
});

export {MigrateAction};
