/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react';
import {Outlet, useLocation, useMatch, useNavigate} from 'react-router-dom';
import {processInstanceMigrationStore} from 'modules/stores/processInstanceMigration';
import {Paths} from 'modules/Routes';
import {MULTI_VARIABLE_FILTER} from 'modules/feature-flags';
import {MigrationView} from './MigrationView';
import {ListView} from './ListView';
import {useEffect} from 'react';
import {useCallbackPrompt} from 'modules/hooks/useCallbackPrompt';
import {Modal} from '@carbon/react';

const Processes: React.FC = observer(() => {
  const isOnVariablesRoute = useMatch(Paths.processesVariables()) !== null;
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    return processInstanceMigrationStore.reset;
  }, []);

  useEffect(() => {
    if (isOnVariablesRoute && !MULTI_VARIABLE_FILTER) {
      navigate(
        {pathname: Paths.processes(), search: location.search},
        {replace: true},
      );
    }
  }, [isOnVariablesRoute, navigate, location.search]);

  const {isNavigationInterrupted, confirmNavigation, cancelNavigation} =
    useCallbackPrompt({
      shouldInterrupt: processInstanceMigrationStore.isEnabled,
    });

  return (
    <>
      {processInstanceMigrationStore.isEnabled ? (
        <MigrationView />
      ) : (
        <ListView />
      )}
      <Outlet />

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
