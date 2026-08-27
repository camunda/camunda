/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, { createGlobalStyle } from "styled-components";
import { FC, ReactNode, useEffect } from "react";
import { styles } from "@carbon/elements";
import {
  IS_NAV_V2_ENABLED,
  IS_NEW_DESIGN_SYSTEM_ENABLED,
} from "src/feature-flags";
import AppHeader from "src/components/layout/AppHeader";
import ErrorBoundary from "src/components/global/ErrorBoundary";
import { useQuery } from "@tanstack/react-query";
import { useSessionHeartbeat } from "@camunda/session-heartbeat/react";
import { authenticationQueries } from "src/utility/api/authentication/queries";
import ForbiddenComponentV1 from "src/pages/forbidden/ForbiddenPage";
import ForbiddenComponentV2 from "src/pages/forbiddenV2/ForbiddenPage";
import LateLoading from "src/components/layout/LateLoading";
import { activateSession, disableSession } from "src/utility/auth";
import { ApiError, getCsrfToken } from "src/utility/api/request";
import { notifyApiError } from "src/utility/api/errorNotification";
import { queryClient } from "src/utility/api/queryClient";
import { getSessionHeartbeatApiUrl } from "src/configuration/urlConfig";
import { C3Provider } from "../layout/C3Provider";
import { ThemeProvider } from "src/common/theme/ThemeProvider";

const ForbiddenComponent = IS_NEW_DESIGN_SYSTEM_ENABLED
  ? ForbiddenComponentV2
  : ForbiddenComponentV1;

const GlobalStyle = createGlobalStyle`
  body {
    background: var(--cds-background);
    font-size: ${styles.bodyShort01.fontSize};
    font-weight: ${styles.bodyShort01.fontWeight};
    line-height: ${styles.bodyShort01.lineHeight};
    letter-spacing: ${styles.bodyShort01.letterSpacing};
    margin: 0;
    padding: 0;
    box-sizing: border-box;
    -webkit-font-smoothing: antialiased;
  }
  * {
    box-sizing: border-box;
  }
`;

const AppRootWrapper = styled.div`
  height: 100vh;
  display: grid;
  grid-template-columns: 1fr;
  grid-template-rows: auto 1fr;
  grid-template-areas:
    "header"
    "main";
  position: relative;
`;

const GridHeader = styled.div`
  grid-area: header;
`;

const GridMain = styled.div`
  grid-area: main;
  overflow: auto;
  position: relative;
  display: grid;
  grid-template-rows: 1fr auto;
  grid-template-columns: 1fr;
  /* The design-system header sits in the grid's header row, so the content row
     needs no offset. The legacy Carbon header is fixed and does. */
  padding-top: ${IS_NAV_V2_ENABLED ? "0" : "var(--c3-header-height, 48px)"};
`;

const GridMainContent = styled.div`
  grid-area: 1 / 1 / 1 / 4;
  /* Variable is set by "SidebarProvider". Or overwritten in this
  component "AppContent" if no sidebar should be visible.  */
  padding-left: var(--app-sidebar-width, 0);
  transition: padding-left 0.15s ease-out;
`;

const AppContent: FC<{ children?: ReactNode }> = ({ children }) => {
  const { data: camundaUser, isLoading: loading } = useQuery(
    authenticationQueries.me(),
  );

  useSessionHeartbeat({
    enabled: camundaUser !== undefined,
    url: getSessionHeartbeatApiUrl(),
    csrfToken: getCsrfToken,
    onUnauthorized: () => {
      // Dispatch while isLoggedIn() is still true so the "session expired" toast
      // and redirect fire immediately, the same way a real request's 401 would.
      // disableSession() must run after, not before, or ErrorNotificationBridge
      // silently swallows this notification (and the real request's 401 that
      // follows it, once the cleared query cache refetches and 401s again).
      notifyApiError(new ApiError(401, null), { skipToast: false });
      disableSession();
      queryClient.clear();
    },
  });

  useEffect(() => {
    if (camundaUser) {
      activateSession();
    }
  }, [camundaUser]);

  if (loading) {
    return <LateLoading />;
  }

  if (
    !camundaUser?.authorizedComponents.includes("admin") &&
    !camundaUser?.authorizedComponents.includes("*")
  ) {
    return (
      <>
        <GridHeader>
          <AppHeader hideNavLinks />
        </GridHeader>
        <GridMain style={{ "--app-sidebar-width": 0 }}>
          <GridMainContent id="main-content" tabIndex={-1}>
            <ForbiddenComponent />
          </GridMainContent>
        </GridMain>
      </>
    );
  }
  return (
    <>
      <GridHeader>
        <AppHeader />
      </GridHeader>
      <GridMain>
        <GridMainContent id="main-content" tabIndex={-1}>
          {children}
        </GridMainContent>
      </GridMain>
    </>
  );
};

const AppRoot: FC<{ children?: ReactNode }> = ({ children }) => (
  <ThemeProvider>
    <AppRootWrapper>
      <ErrorBoundary>
        <C3Provider>
          <GlobalStyle />
          <AppContent>{children}</AppContent>
        </C3Provider>
      </ErrorBoundary>
    </AppRootWrapper>
  </ThemeProvider>
);

export default AppRoot;
