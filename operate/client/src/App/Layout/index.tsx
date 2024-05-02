/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Outlet} from 'react-router-dom';
import {AppHeader} from './AppHeader';
import {PageContent} from './styled';
import {observer} from 'mobx-react';

const Layout: React.FC = observer(() => {
  return (
    <>
      <AppHeader />
      <PageContent id="main-content">
        <Outlet />
      </PageContent>
    </>
  );
});

export {Layout};
