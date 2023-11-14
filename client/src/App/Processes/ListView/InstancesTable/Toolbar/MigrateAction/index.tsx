/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {ListItem, Modal} from './styled';
import {processStatisticsStore as processStatisticsMigrationSourceStore} from 'modules/stores/processStatistics/processStatistics.migration.source';

const MigrateAction: React.FC = observer(() => {
  const location = useLocation();
  const {version, process, tenant} = getProcessInstanceFilters(location.search);
  const {
    selectedProcessInstanceIds,
    state: {isAllChecked},
  } = processInstancesSelectionStore;

  const isVersionSelected = version !== undefined && version !== 'all';
  const hasSelectedFinishedInstances =
    processInstancesSelectionStore.state.selectionMode === 'ALL' ||
    processInstancesStore.state.processInstances.some((processInstance) => {
      return (
        selectedProcessInstanceIds.includes(processInstance.id) &&
        ['ACTIVE', 'INCIDENT'].includes(processInstance.state)
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
      <ModalStateManager
        renderLauncher={({setOpen}) => (
          <TableBatchAction
            renderIcon={MigrateAlt}
            onClick={() => {
              setOpen(true);
            }}
            disabled={isDisabled}
            title={getTooltipText()}
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
              processStatisticsMigrationSourceStore.fetchProcessStatistics({
                ids: isAllChecked ? [] : selectedProcessInstanceIds,
                excludeIds: isAllChecked ? selectedProcessInstanceIds : [],
              });
              processInstanceMigrationStore.setSelectedInstancesCount(
                processInstancesSelectionStore.selectedProcessInstanceCount,
              );
              processInstanceMigrationStore.enable();
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
                When Migrate steps are run, all process instances will be
                affected. Interruptions, delays or changes may happen as a
                result.
              </ListItem>
              <ListItem>
                To minimize interruptions or delays, schedule Migrate during
                periods of low system usage.
              </ListItem>
            </Stack>
            <p>
              Questions or concerns? Check our{' '}
              <Link
                href="https://docs.camunda.io/docs/components/operate/operate-introduction/"
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
