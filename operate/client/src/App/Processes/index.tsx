/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {observer} from 'mobx-react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {MigrationView} from './MigrationView';
import {ListView} from './ListView';
import {useEffect} from 'react';
import {useCallbackPrompt} from 'modules/hooks/useCallbackPrompt';
import {Modal} from '@carbon/react';

const Processes: React.FC = observer(() => {
  const {hasPendingRequest} = processInstanceMigrationStore.state;

  useEffect(() => {
    return processInstanceMigrationStore.reset;
  }, []);

  const {showPrompt, confirmNavigation, cancelNavigation} = useCallbackPrompt(
    processInstanceMigrationStore.isEnabled,
  );

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

      {showPrompt && !hasPendingRequest && (
        <Modal
          open={showPrompt}
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
