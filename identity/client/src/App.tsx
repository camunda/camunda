/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, StrictMode, useEffect } from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { QueryClientProvider } from "@tanstack/react-query";
import AppRoot from "./components/global/AppRoot";
import GlobalRoutes from "src/components/global/GlobalRoutes";
import { LoginPage as LoginPageV1 } from "src/pages/login/LoginPage.tsx";
import { LoginPage as LoginPageV2 } from "src/pages/loginV2/LoginPage.tsx";
import ForbiddenV1 from "src/pages/forbidden/index.tsx";
import ForbiddenV2 from "src/pages/forbiddenV2/index.tsx";
import { NotificationProvider as NotificationProviderV1 } from "src/components/notifications";
import NotificationProviderV2 from "src/components/notificationsV2/NotificationProvider";
import { Paths } from "src/components/global/routePaths";
import { SetupPage as SetupPageV1 } from "src/pages/setup/SetupPage";
import { SetupPage as SetupPageV2 } from "src/pages/setupV2/SetupPage";
import { cleanServiceWorkers } from "src/utility/cleanServiceWorkers.ts";
import { getBaseUrl } from "./configuration/urlConfig";
import { DocsUrlProvider } from "./components/documentation/DocsUrlContext.tsx";
import { docsUrl } from "src/configuration";
import { queryClient } from "src/utility/api/queryClient";
import ErrorNotificationBridge from "src/utility/api/ErrorNotificationBridge";
import { IS_NEW_DESIGN_SYSTEM_ENABLED } from "./feature-flags.ts";

const NotificationProvider = IS_NEW_DESIGN_SYSTEM_ENABLED
  ? NotificationProviderV2
  : NotificationProviderV1;

const LoginPage = IS_NEW_DESIGN_SYSTEM_ENABLED ? LoginPageV2 : LoginPageV1;
const Forbidden = IS_NEW_DESIGN_SYSTEM_ENABLED ? ForbiddenV2 : ForbiddenV1;
const SetupPage = IS_NEW_DESIGN_SYSTEM_ENABLED ? SetupPageV2 : SetupPageV1;

const App: FC = () => {
  useEffect(() => {
    void cleanServiceWorkers();
  });

  return (
    <BrowserRouter basename={getBaseUrl()}>
      <StrictMode>
        <QueryClientProvider client={queryClient}>
          <DocsUrlProvider value={docsUrl}>
            <NotificationProvider>
              <ErrorNotificationBridge />
              <Routes>
                <Route key="setup" path={Paths.setup()} Component={SetupPage} />
                <Route
                  key="login"
                  path={Paths.login()}
                  element={
                    <LoginPage
                      defaultRedirectUrl={getBaseUrl() + Paths.users()}
                    />
                  }
                />
                <Route path={Paths.forbidden()} element={<Forbidden />} />
                <Route
                  key="identity-ui"
                  path="*"
                  element={
                    <AppRoot>
                      <GlobalRoutes />
                    </AppRoot>
                  }
                />
              </Routes>
            </NotificationProvider>
          </DocsUrlProvider>
        </QueryClientProvider>
      </StrictMode>
    </BrowserRouter>
  );
};

export default App;
