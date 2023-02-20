/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Outlet, useMatch} from 'react-router-dom';
import {AppHeader} from './AppHeader';
import {Footer, Grid, PageContent} from './styled';
import {Copyright} from 'modules/components/Copyright';
import {Paths} from 'modules/routes';
import {modificationsStore} from 'modules/stores/modifications';
import {observer} from 'mobx-react';
import {OperationsPanel} from 'modules/components/OperationsPanel';

const Layout: React.FC = observer(() => {
  const instancesMatch = useMatch(Paths.processes());
  const decisionsMatch = useMatch(Paths.decisions());
  const dashboardMatch = useMatch(Paths.dashboard());
  const showFooter =
    instancesMatch === null &&
    decisionsMatch === null &&
    !modificationsStore.isModificationModeEnabled;
  const showOperationsPanel =
    instancesMatch !== null || decisionsMatch !== null;

  return (
    <Grid numberOfRows={showFooter ? 3 : 2}>
      <AppHeader />
      {showOperationsPanel && <OperationsPanel />}
      <PageContent>
        <Outlet />
      </PageContent>
      {showFooter && (
        <Footer variant={dashboardMatch === null ? 'default' : 'dashboard'}>
          <Copyright />
        </Footer>
      )}
    </Grid>
  );
});

export {Layout};
