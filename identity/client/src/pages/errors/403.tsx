/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// src/pages/Error403.tsx
import {FC} from 'react';
import useTranslate from "src/utility/localization";
import ErrorPage from "src/components/global/ErrorPage.tsx";
import {Link} from "@carbon/react";

const Error403: FC = () => {
  const { Translate } = useTranslate();

  return (
      <ErrorPage title={<Translate>Failmunda - 403 forbidden</Translate>}>
        <Translate>
          You do not have permission to access this page, sorry!
        </Translate>
        <Link href="/">
          <Translate>Click here to go back home</Translate>
        </Link>
      </ErrorPage>
  );
};

export default Error403;
