/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { C3UserConfigurationProvider } from "@camunda/camunda-composite-components";
import { C3ThemePersister } from "src/common/theme/C3ThemePersister";
import { useEffect } from "react";
import { useMutation } from "@tanstack/react-query";
import { authenticationMutations } from "src/utility/api/authentication/mutations";
import { getStage } from "src/utility/getStage";

const STAGE = getStage(window.location.host);

type Props = {
  children: React.ReactNode;
};

const C3Provider: React.FC<Props> = ({ children }) => {
  const {
    mutate,
    mutateAsync,
    data: token,
  } = useMutation(authenticationMutations.saasUserToken());
  const organizationId = window.clientConfig?.organizationId;
  const clusterId = window.clientConfig?.clusterId;

  useEffect(() => {
    if (typeof organizationId === "string") {
      mutate();
    }
  }, [mutate, organizationId]);

  if (
    !token ||
    typeof organizationId !== "string" ||
    typeof clusterId !== "string"
  ) {
    return children;
  }

  return (
    <C3UserConfigurationProvider
      activeOrganizationId={organizationId}
      currentClusterUuid={clusterId}
      userToken={token}
      getNewUserToken={async () => (await mutateAsync()) || ""}
      currentApp="identity"
      stage={STAGE === "unknown" ? "dev" : STAGE}
      handleTheme
    >
      <C3ThemePersister />
      {children}
    </C3UserConfigurationProvider>
  );
};

export { C3Provider };
