/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from "styled-components";
import { FC, ReactNode } from "react";
import Failmunda from "src/assets/images/failmunda.svg";
import Flex from "src/components/layout/Flex";
import { cssSize } from "src/utility/style.ts";
import Page from "src/components/layout/Page";

const CenterWrapper = styled(Flex)`
  margin: ${cssSize(10)} auto;
  max-width: ${cssSize(50)};
  align-items: start;
  flex-direction: column;
`;

type ErrorPageProps = {
  title: ReactNode;
  children?: ReactNode;
};

const ErrorPage: FC<ErrorPageProps> = ({ title, children }) => (
  <Page>
    <CenterWrapper>
      <Failmunda />
      <h1>{title}</h1>
      {children}
    </CenterWrapper>
  </Page>
);

export default ErrorPage;
