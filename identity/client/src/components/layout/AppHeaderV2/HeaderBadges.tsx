/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useC3Profile } from "@camunda/camunda-composite-components";
import { EnvironmentBadge, useIsMobile } from "@camunda/design-system";
import { useQuery } from "@tanstack/react-query";

import { licenseQueries } from "src/utility/api/headers/queries.ts";

import { LicenseBadges } from "./LicenseBadges";

const HeaderBadges = () => {
  const { clusters } = useC3Profile();
  const { data: license } = useQuery(licenseQueries.current());
  const isMobile = useIsMobile();
  const clusterId = window.clientConfig?.clusterId;
  const stage =
    clusters?.find(({ uuid }) => uuid === clusterId)?.labels?.camunda?.[0] ??
    "";

  return (
    <div className="flex items-center gap-2">
      <EnvironmentBadge stage={stage} />
      {isMobile ? null : <LicenseBadges license={license} />}
    </div>
  );
};

export { HeaderBadges };
