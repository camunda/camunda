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
import {CarbonPaths} from 'modules/carbonRoutes';
import {modificationsStore} from 'modules/stores/modifications';
import {observer} from 'mobx-react';
import {OperationsPanel} from 'modules/components/OperationsPanel';

const Layout: React.FC = observer(() => {
  const instancesMatch = useMatch(CarbonPaths.processes());
  const decisionsMatch = useMatch(CarbonPaths.decisions());
  const dashboardMatch = useMatch(CarbonPaths.dashboard());
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
        <div>this is the carbon layout</div>
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
