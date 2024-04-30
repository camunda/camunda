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
import {processInstancesStore} from 'modules/stores/processInstances';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {processXmlStore as processXmlMigrationSourceStore} from 'modules/stores/processXml/processXml.migration.source';
import {processXmlStore} from 'modules/stores/processXml/processXml.list';
import {processesStore} from 'modules/stores/processes/processes.list';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import {processStatisticsStore as processStatisticsMigrationSourceStore} from 'modules/stores/processStatistics/processStatistics.migration.source';
import {getProcessInstancesRequestFilters} from 'modules/utils/filter';
import {ListItem, Modal} from './styled';
import isNil from 'lodash/isNil';
import {tracking} from 'modules/tracking';
import {batchModificationStore} from 'modules/stores/batchModification';

const MigrateAction: React.FC = observer(() => {
  const location = useLocation();
  const {version, process, tenant} = getProcessInstanceFilters(location.search);
  const {
    selectedProcessInstanceIds,
    hasSelectedRunningInstances,
    state: {isAllChecked},
  } = processInstancesSelectionStore;

  const isVersionSelected = version !== undefined && version !== 'all';

  const isChildProcess = (() => {
    if (processInstancesSelectionStore.state.isAllChecked) {
      return processInstancesStore.state.processInstances.some(
        ({parentInstanceId}) => parentInstanceId !== null,
      );
    }

    return processInstancesSelectionStore.state.selectedProcessInstanceIds.some(
      (processInstanceId) => {
        const instance = processInstancesStore.state.processInstances.find(
          ({id}) => {
            return id === processInstanceId;
          },
        );
        return !isNil(instance?.parentInstanceId);
      },
    );
  })();

  const hasXmlError = processXmlStore.state.status === 'error';

  const isDisabled =
    batchModificationStore.state.isEnabled ||
    !isVersionSelected ||
    !hasSelectedRunningInstances ||
    isChildProcess ||
    hasXmlError;

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

    if (isChildProcess) {
      return 'You can only migrate instances which are not called by a parent process';
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
      <ModalStateManager
        renderLauncher={({setOpen}) => (
          <TableBatchAction
            renderIcon={MigrateAlt}
            onClick={() => {
              setOpen(true);
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
          <Modal
            open={open}
            preventCloseOnClickOutside
            modalHeading="Migrate process instance versions"
            primaryButtonText="Continue"
            secondaryButtonText="Cancel"
            onRequestSubmit={() => {
              processXmlMigrationSourceStore.setProcessXml(
                processXmlStore.state.xml,
              );

              const requestFilterParameters = {
                ...getProcessInstancesRequestFilters(),
                ids: isAllChecked ? [] : selectedProcessInstanceIds,
                excludeIds: isAllChecked ? selectedProcessInstanceIds : [],
              };

              processStatisticsMigrationSourceStore.fetchProcessStatistics(
                requestFilterParameters,
              );
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
            }}
            onRequestClose={() => setOpen(false)}
            onSecondarySubmit={() => setOpen(false)}
            size="md"
          >
            <Stack as={OrderedList} nested gap={5}>
              <ListItem>
                Migrate is used to move a process to a newer (or older) version
                of the process.
              </ListItem>
              <ListItem>
                When the migration steps are executed, all process instances are
                affected. This can lead to interruptions, delays, or changes.
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
          </Modal>
        )}
      </ModalStateManager>
    </Restricted>
  );
});

export {MigrateAction};
