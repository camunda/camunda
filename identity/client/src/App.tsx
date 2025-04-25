/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, StrictMode } from "react";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import { getBaseUrl } from "./configuration";
import AppRoot from "./components/global/AppRoot";
import GlobalRoutes from "src/components/global/GlobalRoutes";
import { LoginPage } from "src/pages/login/LoginPage.tsx";
import Forbidden from "src/pages/forbidden/index.tsx";
import { NotificationProvider } from "src/components/notifications";
import { Paths } from "src/components/global/routePaths";

const App: FC = () => (
  <BrowserRouter basename={getBaseUrl()}>
    <StrictMode>
      <NotificationProvider>
        <Routes>
          <Route key="login" path={Paths.login()} Component={LoginPage} />
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
    </StrictMode>
  </BrowserRouter>
);

export default App;
