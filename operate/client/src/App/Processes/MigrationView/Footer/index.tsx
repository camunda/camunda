/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Button, Modal} from '@carbon/react';
import {observer} from 'mobx-react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {Container} from './styled';
import {ModalStateManager} from 'modules/components/ModalStateManager';
import {processesStore} from 'modules/stores/processes/processes.migration';
import {useNavigate} from 'react-router-dom';
import {Locations} from 'modules/Routes';
import {tracking} from 'modules/tracking';

const Footer: React.FC = observer(() => {
  const navigate = useNavigate();

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
          <Button
            aria-label="Confirm"
            size="sm"
            onClick={() => {
              const {selectedTargetProcess, selectedTargetVersion} =
                processesStore.migrationState;

              processInstanceMigrationStore.setHasPendingRequest();
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
          >
            Confirm
          </Button>
        </>
      )}
    </Container>
  );
});

export {Footer};
