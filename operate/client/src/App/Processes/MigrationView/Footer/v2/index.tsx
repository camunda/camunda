/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Modal} from '@carbon/react';
import {observer} from 'mobx-react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {Container} from '../styled.tsx';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {useNavigate} from 'react-router-dom';
import {Locations} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {MigrationConfirmationModal} from '../../MigrationConfirmationModal';
import {useMigrateProcessInstanceBatchOperation} from 'modules/mutations/processInstance/useMigrateProcessInstanceBatchOperation';
import {notificationsStore} from 'modules/stores/notifications';
import {useProcessInstanceFilters} from 'modules/hooks/useProcessInstancesFilters';
import {getMigrationBatchOperationFilter} from './getMigrationBatchOperationFilter';
import {extractIdsFromQuery} from './extractIdsFromQuery';
import {panelStatesStore} from 'modules/stores/panelStates';

const Footer: React.FC = observer(() => {
  const baseFilter = useProcessInstanceFilters().filter;

  const navigate = useNavigate();

  const mutation = useMigrateProcessInstanceBatchOperation({
    onSuccess: () => {
      tracking.track({
        eventName: 'batch-operation',
        operationType: 'MIGRATE_PROCESS_INSTANCE',
      });
    },
    onError: ({message}) =>
      notificationsStore.displayNotification({
        kind: 'error',
        title: 'Operation could not be created',
        subtitle: message.includes('403')
          ? 'You do not have permission'
          : undefined,
        isDismissable: true,
      }),
  });

  return (
    <Container orientation="horizontal" gap={5}>
      <ModalStateManager
        renderLauncher={({setOpen}) => (
          <Button
            kind="secondary"
            size="sm"
            onClick={() => {
              setOpen(true);
            }}
          >
            Exit migration
          </Button>
        )}
      >
        {({open, setOpen}) => (
          <Modal
            open={open}
            danger
            preventCloseOnClickOutside
            modalHeading="Exit migration"
            primaryButtonText="Exit"
            secondaryButtonText="Cancel"
            onRequestSubmit={() => {
              setOpen(false);
              processInstanceMigrationStore.disable();
            }}
            onRequestClose={() => setOpen(false)}
            size="md"
          >
            <p>
              You are about to leave ongoing migration, all planned mapping/s
              will be discarded.
            </p>
            <p>Click “Exit” to proceed.</p>
          </Modal>
        )}
      </ModalStateManager>
      {processInstanceMigrationStore.state.currentStep === 'elementMapping' && (
        <Button
          size="sm"
          onClick={() =>
            processInstanceMigrationStore.setCurrentStep('summary')
          }
          disabled={!processInstanceMigrationStore.hasFlowNodeMapping}
          title={
            !processInstanceMigrationStore.hasFlowNodeMapping
              ? 'Please map at least one element to continue'
              : undefined
          }
        >
          Next
        </Button>
      )}
      {processInstanceMigrationStore.state.currentStep === 'summary' && (
        <>
          <Button
            kind="secondary"
            size="sm"
            onClick={() =>
              processInstanceMigrationStore.setCurrentStep('elementMapping')
            }
          >
            Back
          </Button>

          <ModalStateManager
            renderLauncher={({setOpen}) => (
              <Button
                aria-label="Confirm"
                size="sm"
                onClick={() => {
                  setOpen(true);
                }}
              >
                Confirm
              </Button>
            )}
          >
            {({open, setOpen}) => (
              <MigrationConfirmationModal
                open={open}
                setOpen={setOpen}
                onSubmit={() => {
                  const {selectedTargetProcess, selectedTargetVersion} =
                    processesStore.migrationState;

                  const {
                    flowNodeMapping,
                    batchOperationQuery,
                    targetProcessDefinitionKey,
                    sourceProcessDefinitionKey,
                  } = processInstanceMigrationStore.state;

                  if (!batchOperationQuery || !targetProcessDefinitionKey) {
                    return;
                  }

                  const {ids, excludeIds} =
                    extractIdsFromQuery(batchOperationQuery);

                  const filter = getMigrationBatchOperationFilter({
                    ids,
                    excludeIds,
                    sourceProcessDefinitionKey,
                    baseFilter,
                  });

                  mutation.mutate({
                    filter,
                    migrationPlan: {
                      targetProcessDefinitionKey,
                      mappingInstructions: Object.entries(flowNodeMapping).map(
                        ([sourceElementId, targetElementId]) => ({
                          sourceElementId,
                          targetElementId,
                        }),
                      ),
                    },
                  });

                  panelStatesStore.expandOperationsPanel();
                  processInstanceMigrationStore.disable();

                  tracking.track({
                    eventName: 'process-instance-migration-confirmed',
                  });

                  navigate(
                    Locations.processes({
                      active: true,
                      incidents: true,
                      ...(selectedTargetProcess
                        ? {process: selectedTargetProcess.bpmnProcessId}
                        : {}),
                      ...(selectedTargetVersion
                        ? {version: selectedTargetVersion.toString()}
                        : {}),
                    }),
                  );
                }}
              />
            )}
          </ModalStateManager>
        </>
      )}
    </Container>
  );
});

export {Footer};
