/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {lazy, Suspense} from 'react';

import {IS_NAV_V2_ENABLED} from 'feature-flags';

import LegacyHeader from './Header';

// Lazy so the design system's bundle and stylesheet stay off the legacy path.
const HeaderV2 = lazy(() => import('./HeaderV2'));

export const Header = IS_NAV_V2_ENABLED
  ? (props: {noActions?: boolean}) => (
      <Suspense fallback={null}>
        <HeaderV2 {...props} />
      </Suspense>
    )
  : LegacyHeader;
