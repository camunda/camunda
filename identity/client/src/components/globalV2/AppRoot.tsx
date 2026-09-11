/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { type CSSProperties, FC, ReactNode, useEffect } from "react";
import AppHeader from "src/components/layout/AppHeaderV2";
import ErrorBoundary from "src/components/globalV2/ErrorBoundary";
import { useQuery } from "@tanstack/react-query";
import { useSessionHeartbeat } from "@camunda/session-heartbeat/react";
import { authenticationQueries } from "src/utility/api/authentication/queries";
import ForbiddenComponent from "src/pages/forbiddenV2/ForbiddenPage";
import LateLoading from "src/components/layoutV2/LateLoading";
import { activateSession, disableSession } from "src/utility/auth";
import { ApiError, getCsrfToken } from "src/utility/api/request";
import { notifyApiError } from "src/utility/api/errorNotification";
import { queryClient } from "src/utility/api/queryClient";
import { getSessionHeartbeatApiUrl } from "src/configuration/urlConfig";
import { C3Provider } from "../layout/C3Provider";
import { cn } from "@camunda/design-system";

const mainGrid = cn(
  "relative grid grid-cols-[1fr] grid-rows-[1fr_auto] overflow-auto",
);
const mainContent = cn(
  "pl-(--app-sidebar-width) transition-[padding-left] duration-150 ease-out",
);

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
        <AppHeader hideNavLinks />
        <div
          className={mainGrid}
          /* --app-sidebar-width is set by SidebarProvider, or overwritten here when no sidebar is shown. */
          style={{ "--app-sidebar-width": 0 } as CSSProperties}
        >
          <div id="main-content" tabIndex={-1} className={mainContent}>
            <ForbiddenComponent />
          </div>
        </div>
      </>
    );
  }
  return (
    <>
      <AppHeader />
      <div className={mainGrid}>
        <div id="main-content" tabIndex={-1} className={mainContent}>
          {children}
        </div>
      </div>
    </>
  );
};

const AppRoot: FC<{ children?: ReactNode }> = ({ children }) => (
  <div className="relative grid h-dvh grid-cols-[1fr] grid-rows-[auto_1fr]">
    <ErrorBoundary>
      <C3Provider>
        <AppContent>{children}</AppContent>
      </C3Provider>
    </ErrorBoundary>
  </div>
);

export default AppRoot;
