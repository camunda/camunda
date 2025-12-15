/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {MigrationView} from './MigrationView';
import {ListView} from './ListView';
import {ListView as ListViewV2} from './ListView/v2';
import {useEffect} from 'react';
import {useCallbackPrompt} from 'modules/hooks/useCallbackPrompt';
import {Modal} from '@carbon/react';
import {getClientConfig} from 'modules/utils/getClientConfig';

const Processes: React.FC = observer(() => {
  useEffect(() => {
    return processInstanceMigrationStore.reset;
  }, []);

  const {isNavigationInterrupted, confirmNavigation, cancelNavigation} =
    useCallbackPrompt({
      shouldInterrupt: processInstanceMigrationStore.isEnabled,
    });

  return (
    <>
      {processInstanceMigrationStore.isEnabled ? (
        <MigrationView />
      ) : getClientConfig()?.databaseType === 'rdbms' ? (
        <ListViewV2 />
      ) : (
        <ListView />
      )}

      {processInstanceMigrationStore.isEnabled && isNavigationInterrupted && (
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
