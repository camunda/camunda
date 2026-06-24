/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { IS_NAV_V2_ENABLED } from "src/feature-flags";
import AppHeaderV2 from "src/components/layout/AppHeaderV2";
import LegacyAppHeader from "src/components/layout/LegacyAppHeader";

const AppHeader = ({ hideNavLinks = false }: { hideNavLinks?: boolean }) =>
  IS_NAV_V2_ENABLED ? (
    <AppHeaderV2 hideNavLinks={hideNavLinks} />
  ) : (
    <LegacyAppHeader hideNavLinks={hideNavLinks} />
  );

export default AppHeader;
