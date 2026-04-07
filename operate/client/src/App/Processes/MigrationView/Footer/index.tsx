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
import {Container} from './styled';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import {AsyncActionTrigger} from 'modules/components/AsyncActionTrigger';
import {useNavigate, useSearchParams} from 'react-router-dom';
import {Locations} from 'modules/Routes';
import {tracking} from 'modules/tracking';
import {MigrationConfirmationModal} from '../MigrationConfirmationModal';
import {useMigrateProcessInstancesBatchOperation} from 'modules/mutations/processes/useMigrateProcessInstancesBatchOperation';
import {handleOperationError} from 'modules/utils/notifications';
import {processInstancesSelectionStore} from 'modules/stores/processInstancesSelection';
import {buildMutationRequestBody} from 'modules/utils/buildMutationRequestBody';
import {useBatchOperationSuccessNotification} from 'modules/hooks/useBatchOperationSuccessNotification';

const Footer: React.FC = observer(() => {
  const [searchParams] = useSearchParams();
  const displaySuccessNotification = useBatchOperationSuccessNotification();
  const navigate = useNavigate();
  const {
    mutate: migrateProcess,
    reset: resetMigrate,
    status: migrateStatus,
  } = useMigrateProcessInstancesBatchOperation({
    onSuccess: ({batchOperationKey, batchOperationType}) => {
      displaySuccessNotification(batchOperationType, batchOperationKey);
      tracking.track({
        eventName: 'batch-operation',
        operationType: 'MIGRATE_PROCESS_INSTANCE',
      });
    },
    onError: (error) => {
      return handleOperationError(error.response?.status);
    },
  });
  const isMigratePending = migrateStatus === 'pending';

  return (
    <Container orientation="horizontal" gap={5}>
      <ModalStateManager
        renderLauncher={({setOpen}) => (
          <Button
            kind="secondary"
            size="sm"
            disabled={isMigratePending}
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
              processInstancesSelectionStore.reset();
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
          disabled={!processInstanceMigrationStore.hasElementMapping}
          title={
            !processInstanceMigrationStore.hasElementMapping
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
            disabled={isMigratePending}
            onClick={() =>
              processInstanceMigrationStore.setCurrentStep('elementMapping')
            }
          >
            Back
          </Button>

          <ModalStateManager
            renderLauncher={({setOpen}) => (
              <AsyncActionTrigger
                status={migrateStatus}
                pendingLabel="Migrating..."
                onReset={resetMigrate}
              >
                <Button
                  aria-label="Confirm"
                  size="sm"
                  onClick={() => {
                    setOpen(true);
                  }}
                >
                  Confirm
                </Button>
              </AsyncActionTrigger>
            )}
          >
            {({open, setOpen}) => (
              <MigrationConfirmationModal
                open={open}
                setOpen={setOpen}
                onSubmit={() => {
                  const {
                    elementMapping,
                    batchOperationQuery,
                    targetProcessDefinition,
                    sourceProcessDefinition,
                  } = processInstanceMigrationStore.state;

                  if (!batchOperationQuery || !targetProcessDefinition) {
                    return;
                  }

                  const includeIds =
                    'ids' in batchOperationQuery
                      ? (batchOperationQuery.ids ?? [])
                      : [];
                  const excludeIds =
                    'excludeIds' in batchOperationQuery
                      ? batchOperationQuery.excludeIds
                      : [];
                  const variable =
                    'variable' in batchOperationQuery
                      ? batchOperationQuery.variable
                      : undefined;

                  const requestBody = buildMutationRequestBody({
                    searchParams,
                    includeIds,
                    excludeIds,
                    variableFilter:
                      variable !== undefined
                        ? {
                            name: variable.name,
                            values: variable.values.join(','),
                          }
                        : undefined,
                    processDefinitionKey:
                      sourceProcessDefinition?.processDefinitionKey,
                  });

                  tracking.track({
                    eventName: 'process-instance-migration-confirmed',
                  });

                  setOpen(false);
                  migrateProcess(
                    {
                      ...requestBody,
                      migrationPlan: {
                        targetProcessDefinitionKey:
                          targetProcessDefinition.processDefinitionKey,
                        mappingInstructions: Object.entries(elementMapping).map(
                          ([sourceElementId, targetElementId]) => ({
                            sourceElementId,
                            targetElementId,
                          }),
                        ),
                      },
                    },
                    {
                      onSuccess: () => {
                        processInstanceMigrationStore.disable();
                        processInstancesSelectionStore.reset();
                        navigate(
                          Locations.processes({
                            active: true,
                            incidents: true,
                            processDefinitionId:
                              targetProcessDefinition.processDefinitionId,
                            processDefinitionVersion:
                              targetProcessDefinition.version.toString(),
                          }),
                        );
                      },
                    },
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
