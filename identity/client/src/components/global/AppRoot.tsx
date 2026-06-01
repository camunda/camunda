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
import AppHeader from "src/components/layout/AppHeader";
import ErrorBoundary from "src/components/global/ErrorBoundary";
import { useQuery } from "@tanstack/react-query";
import { authenticationQueries } from "src/utility/api/authentication/queries";
import ForbiddenComponent from "src/pages/forbidden/ForbiddenPage";
import LateLoading from "src/components/layout/LateLoading";
import { activateSession } from "src/utility/auth";
import { C3Provider } from "../layout/C3Provider";
import { ThemeProvider } from "src/common/theme/ThemeProvider";

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
  padding-top: 48px;
`;

const GridMainContent = styled.div`
  grid-area: 1 / 1 / 1 / 4;
`;

const AppContent: FC<{ children?: ReactNode }> = ({ children }) => {
  const { data: camundaUser, isLoading: loading } = useQuery(
    authenticationQueries.me(),
  );

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
        <GridMain>
          <GridMainContent>
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
        <GridMainContent>{children}</GridMainContent>
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
