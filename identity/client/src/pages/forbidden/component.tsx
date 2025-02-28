/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { FC } from "react";
import useTranslate from "src/utility/localization";
import ErrorPage from "src/components/global/ErrorPage.tsx";
import { Link } from "@carbon/react";

const ForbiddenComponent: FC = () => {
  const { Translate } = useTranslate();

  return (
    <ErrorPage
      title={
        <Translate>403 - You do not have access to this component</Translate>
      }
    >
      <Translate>
        It looks like you don't have the necessary permissions to access this
        component. Please contact your cluster admin to get access.
      </Translate>
      <Link href="">
        <Translate>Learn more about permissions</Translate>
      </Link>
      <Link href="/">
        <Translate>Click here to go back home</Translate>
      </Link>
    </ErrorPage>
  );
};

export default ForbiddenComponent;
