/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { C3UserConfigurationProvider } from "@camunda/camunda-composite-components";
import { useEffect } from "react";
import { useApiCall } from "src/utility/api";
import { getSaasUserToken } from "src/utility/api/authentication";
import { getStage } from "src/utility/getStage";

declare global {
  interface Window {
    clientConfig?: {
      organizationId?: string;
      clusterId?: string;
    };
  }
}

const STAGE = getStage(window.location.host);

type Props = {
  children: React.ReactNode;
};

const C3Provider: React.FC<Props> = ({ children }) => {
  const [getToken, { data: token }] = useApiCall(getSaasUserToken);
  const organizationId = window.clientConfig?.organizationId;
  const clusterId = window.clientConfig?.clusterId;

  useEffect(() => {
    if (typeof organizationId === "string") {
      // eslint-disable-next-line @typescript-eslint/no-floating-promises
      getToken();
    }
  }, [getToken]);

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
      userToken={token || ""}
      getNewUserToken={async () => {
        const { data } = await getToken();
        return data || "";
      }}
      currentApp="identity"
      stage={STAGE === "unknown" ? "dev" : STAGE}
    >
      {children}
    </C3UserConfigurationProvider>
  );
};

export { C3Provider };
