/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Outlet} from 'react-router-dom';
import {AppHeader} from './AppHeader';
import {PageContent} from './styled';
import {observer} from 'mobx-react';
import 'index-carbon.scss';

const Layout: React.FC = observer(() => {
  return (
    <>
      <AppHeader />
      <PageContent>
        <Outlet />
      </PageContent>
    </>
  );
});

export {Layout};
