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
import {useCallbackPrompt} from 'modules/hooks/useCallbackPrompt';
import {Modal} from '@carbon/react';

const Processes: React.FC = observer(() => {
  const {showPrompt, confirmNavigation, cancelNavigation} = useCallbackPrompt(
    processInstanceMigrationStore.isEnabled,
  );

  return (
    <>
      {processInstanceMigrationStore.isEnabled ? (
        <MigrationView />
      ) : (
        <ListView />
      )}

      {showPrompt && (
        <Modal
          open={showPrompt}
          modalHeading="Leave Migration Mode"
          preventCloseOnClickOutside
          onRequestClose={cancelNavigation}
          secondaryButtonText="Stay"
          primaryButtonText="Leave"
          onRequestSubmit={() => {
            processInstanceMigrationStore.reset();
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
