/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IS_NAV_V2_ENABLED} from 'modules/featureFlags';
import {HeaderV2} from './HeaderV2';
import {LegacyHeader} from './LegacyHeader';
import {CustomFiltersProvider} from 'modules/tasks/available-tasks/CollapsiblePanel/CustomFiltersModal/CustomFiltersProvider';

const Header: React.FC = () =>
  IS_NAV_V2_ENABLED ? (
    <CustomFiltersProvider>
      <HeaderV2 />
    </CustomFiltersProvider>
  ) : (
    <LegacyHeader />
  );

export {Header};
