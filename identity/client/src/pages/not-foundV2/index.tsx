/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { Button } from "@camunda/design-system";
import { FC } from "react";
import useTranslate from "src/utility/localization";
import ErrorPage from "src/components/globalV2/ErrorPage";

const NotFound: FC = () => {
  const { Translate } = useTranslate();

  return (
    <ErrorPage title={<Translate>Failmunda - 404 not found</Translate>}>
      <Translate>
        What you&apos;re looking for isn&apos;t here, sorry!
      </Translate>
      <Button variant="link" size="sm" asChild>
        <a href="/">
          <Translate>Click here to go back home</Translate>
        </a>
      </Button>
    </ErrorPage>
  );
};

export default NotFound;
