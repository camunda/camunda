/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {MigrationView} from '../MigrationView/v2';
import {ListView} from '../ListView';
import {useEffect} from 'react';
import {useCallbackPrompt} from 'modules/hooks/useCallbackPrompt';
import {Modal} from '@carbon/react';

const Processes: React.FC = observer(() => {
  const {hasPendingRequest} = processInstanceMigrationStore.state;

  useEffect(() => {
    return processInstanceMigrationStore.reset;
  }, []);

  const {isNavigationInterrupted, confirmNavigation, cancelNavigation} =
    useCallbackPrompt({
      shouldInterrupt: processInstanceMigrationStore.isEnabled,
    });

  useEffect(() => {
    // this effect is necessary to bypass the callback prompt when a migration
    // is triggered from migration view (MigrationView/Footer/index.tsx)
    if (hasPendingRequest) {
      confirmNavigation();
    }
  }, [hasPendingRequest, confirmNavigation]);

  return (
    <>
      {processInstanceMigrationStore.isEnabled ? (
        <MigrationView />
      ) : (
        <ListView />
      )}

      {isNavigationInterrupted && !hasPendingRequest && (
        <Modal
          open={isNavigationInterrupted}
          modalHeading="Leave Migration Mode"
          preventCloseOnClickOutside
          onRequestClose={cancelNavigation}
          secondaryButtonText="Stay"
          primaryButtonText="Leave"
          onRequestSubmit={() => {
            processInstanceMigrationStore.disable();
            confirmNavigation();
          }}
        >
          <p>By leaving this page, all planned mapping/s will be discarded.</p>
        </Modal>
      )}
    </>
  );
});

export {Processes};
