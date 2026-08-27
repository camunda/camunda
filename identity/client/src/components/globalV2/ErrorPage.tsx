/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { FC, ReactNode } from "react";
import Failmunda from "src/assets/images/failmunda.svg";
import Page from "src/components/layoutV2/Page";

type ErrorPageProps = {
  title: ReactNode;
  children?: ReactNode;
};

const ErrorPage: FC<ErrorPageProps> = ({ title, children }) => (
  <Page>
    <div className="mx-auto my-20 flex max-w-100 flex-col items-start gap-3">
      <Failmunda />
      <h1>{title}</h1>
      {children}
    </div>
  </Page>
);

export default ErrorPage;
