/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Outlet, useLocation} from 'react-router-dom';
import {AppHeader} from './AppHeader';
import {PageWrapper, PageContent, CopilotGlobalContainer} from './styled';
import {observer} from 'mobx-react';
import {C3Provider} from './C3Provider';
import {copilotStore} from 'modules/stores/copilot';
import {CoPilot} from 'App/ProcessInstance/CoPilot';

const Layout: React.FC = observer(() => {
  const {pathname} = useLocation();
  const segments = pathname.split('/').filter(Boolean);
  const isProcessInstancePage =
    segments[0] === 'processes' && segments.length >= 2;

  const showGlobalCopilot = copilotStore.isOpen && !isProcessInstancePage;

  return (
    <C3Provider>
      <AppHeader />
      <PageWrapper>
        <PageContent id="main-content">
          <Outlet />
        </PageContent>
        {showGlobalCopilot && (
          <CopilotGlobalContainer>
            <CoPilot onClose={copilotStore.close} />
          </CopilotGlobalContainer>
        )}
      </PageWrapper>
    </C3Provider>
  );
});

export {Layout};
