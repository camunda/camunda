/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";
import { Stack, Button as BaseButton } from "@carbon/react";
import Page from "src/components/layout/Page.tsx";

const loginPageContentWidth = "25rem";

export const LoginFormContainer = styled(Stack)<{ $hasError: boolean }>`
  flex-direction: column;
  gap: var(--cds-spacing-07);
  width: 100%;
  padding-top: ${({ $hasError }) => ($hasError ? 0 : "var(--cds-spacing-07)")};
  padding-bottom: var(--cds-spacing-07);
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
  margin: var(--cds-spacing-07);
  display: flex;
  flex-direction: column;
  align-items: center;

  h1 {
    font-size: var(--cds-spacing-07);
  }

  img {
    height: var(--cds-spacing-07);
  }
`;

export const LicenseInfo = styled.div`
  width: 100%;
  color: var(--cds-text-secondary);
  font-size: var(--cds-legal-01-font-size);
  font-weight: var(--cds-legal-01-font-weight);
  text-align: center;
  line-height: var(--cds-legal-01-line-height);
  letter-spacing: var(--cds-legal-01-letter-spacing);

  a {
    font-size: var(--cds-legal-01-font-size);
  }
`;

export const CopyrightNotice = styled.div`
  position: absolute;
  text-align: center;
  padding: var(--cds-spacing-05);
  display: block;
  bottom: 0;
  font-size: var(--cds-spacing-04);
`;

export const Button: typeof BaseButton = styled(BaseButton)`
  min-width: 100%;
`;
