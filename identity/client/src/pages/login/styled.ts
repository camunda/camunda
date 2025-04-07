/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";
import { Stack } from "@carbon/react";
import { spacing04, spacing05, spacing07 } from "@carbon/elements";
import Page from "src/components/layout/Page.tsx";

const loginPageContentWidth = "25rem";

export const LoginFormContainer = styled(Stack)<{ $hasError: boolean }>`
  flex-direction: column;
  gap: ${spacing07};
  width: 100%;
  padding-top: ${({ $hasError }) => ($hasError ? 0 : spacing07)};
  padding-bottom: ${spacing07};

  > button {
    max-inline-size: unset;
    width: 100%;
  }
`;

export const LoginPageContainer = styled(Page)`
  display: flex;
  flex-direction: row;
  justify-content: center;
`;

export const Content = styled.div`
  width: ${loginPageContentWidth};
  display: flex;
  flex-direction: column;
`;

export const Header = styled.div`
  margin: ${spacing07};
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

export const LicenseInfo = styled.div`
  width: 100%;
  font-size: ${spacing04};
  font-weight: 400;
  text-align: center;
  line-height: 1.3333;

  a {
    font-size: ${spacing04};
  }
`;

export const CopyrightNotice = styled.div`
  position: absolute;
  text-align: center;
  padding: ${spacing05};
  display: block;
  bottom: 0;
  font-size: ${spacing04};
`;
