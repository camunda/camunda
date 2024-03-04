/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {
  unstable_HistoryRouter as HistoryRouter,
  Route,
  Routes,
} from 'react-router-dom';
import {ThemeProvider} from 'modules/theme/ThemeProvider';
import {Notifications} from 'modules/notifications';
import {NetworkStatusWatcher} from './NetworkStatusWatcher';
import {Paths} from 'modules/Routes';
import {RedirectDeprecatedRoutes} from './RedirectDeprecatedRoutes';
import {AuthenticationCheck} from './AuthenticationCheck';
import {SessionWatcher} from './SessionWatcher';
import {TrackPagination} from 'modules/tracking/TrackPagination';
import {useEffect} from 'react';
import {tracking} from 'modules/tracking';
import {currentTheme} from 'modules/stores/currentTheme';
import {createBrowserHistory} from 'history';
import {ThemeSwitcher} from 'modules/components/ThemeSwitcher';
import loadable from '@loadable/component';

const CarbonLogin = loadable(() => import('./Login/index'), {
  resolveComponent: (components) => components.Login,
});

const CarbonLayout = loadable(() => import('./Layout/index'), {
  resolveComponent: (components) => components.Layout,
});

const CarbonDashboard = loadable(() => import('./Dashboard/index'), {
  resolveComponent: (components) => components.Dashboard,
});

const CarbonDecisions = loadable(() => import('./Decisions/index'), {
  resolveComponent: (components) => components.Decisions,
});

const CarbonProcesses = loadable(() => import('./Processes/index'), {
  resolveComponent: (components) => components.Processes,
});

const CarbonProcessInstance = loadable(
  () => import('./ProcessInstance/index'),
  {
    resolveComponent: (components) => components.ProcessInstance,
  },
);

const CarbonDecisionInstance = loadable(
  () => import('./DecisionInstance/index'),
  {
    resolveComponent: (components) => components.DecisionInstance,
  },
);

const App: React.FC = () => {
  useEffect(() => {
    tracking.track({
      eventName: 'operate-loaded',
      theme: currentTheme.state.selectedTheme,
    });
  }, []);

  return (
    <ThemeProvider>
      <ThemeSwitcher />
      <Notifications />
      <NetworkStatusWatcher />
      <HistoryRouter
        history={createBrowserHistory({window})}
        basename={window.clientConfig?.contextPath ?? '/'}
      >
        <RedirectDeprecatedRoutes />
        <SessionWatcher />
        <TrackPagination />
        <Routes>
          <Route path={Paths.login()} element={<CarbonLogin />} />
          <Route
            path={Paths.dashboard()}
            element={
              <AuthenticationCheck redirectPath={Paths.login()}>
                <CarbonLayout />
              </AuthenticationCheck>
            }
          >
            <Route index element={<CarbonDashboard />} />
            <Route path={Paths.processes()} element={<CarbonProcesses />} />
            <Route
              path={Paths.processInstance()}
              element={<CarbonProcessInstance />}
            />
            <Route path={Paths.decisions()} element={<CarbonDecisions />} />
            <Route
              path={Paths.decisionInstance()}
              element={<CarbonDecisionInstance />}
            />
          </Route>
        </Routes>
      </HistoryRouter>
    </ThemeProvider>
  );
};

export {App};
