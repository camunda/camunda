/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IS_NAV_V2_ENABLED} from 'feature-flags';

import LegacyHeader from './Header';
import HeaderV2 from './HeaderV2';

export const Header = IS_NAV_V2_ENABLED ? HeaderV2 : LegacyHeader;
