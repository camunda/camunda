/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense} from 'react';
import {IS_NAV_V2_ENABLED} from 'modules/feature-flags';
import {LegacyAppHeader} from './LegacyAppHeader';

const AppHeaderV2 = lazy(async () => {
  const {AppHeaderV2: Component} = await import('./AppHeaderV2');
  return {default: Component};
});

const AppHeader: React.FC<{hideNavLinks?: boolean}> = ({
  hideNavLinks = false,
}) =>
  IS_NAV_V2_ENABLED ? (
    <Suspense fallback={null}>
      <AppHeaderV2 hideNavLinks={hideNavLinks} />
    </Suspense>
  ) : (
    <LegacyAppHeader />
  );

export {AppHeader};
