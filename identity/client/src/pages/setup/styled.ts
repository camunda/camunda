/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";
import {
  Stack,
  InlineNotification as BaseInlineNotification,
} from "@carbon/react";
import { spacing06, spacing07 } from "@carbon/elements";
import Page from "src/components/layout/Page.tsx";

const setupPageContentWidth = "25rem";

export const SetupFormContainer = styled(Stack)<{ $hasError: boolean }>`
  flex-direction: column;
  gap: ${spacing06};
  width: 100%;
  padding-top: ${({ $hasError }) => ($hasError ? 0 : spacing06)};

  > button {
    max-inline-size: unset;
    width: 100%;
  }
`;

export const SetupPageContainer = styled(Page)`
  display: flex;
  flex-direction: row;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  padding: ${spacing06};
`;

export const Content = styled.div`
  width: ${setupPageContentWidth};
  display: flex;
  flex-direction: column;
`;

export const PageTitle = styled.h1`
  text-align: center;
`;

export const Header = styled.div`
  margin: ${spacing06};
  margin-top: 0;
  display: flex;
  flex-direction: column;
  align-items: center;

  h1 {
    font-size: ${spacing07};
  }

  img {
    height: ${spacing07};
  }
`;

export const InlineNotification = styled(BaseInlineNotification)`
  margin-top: ${spacing06};
`;
