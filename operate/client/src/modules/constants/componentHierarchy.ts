/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const zLayoutBase = 0;
const zAppHeader = 8000; // defined by Carbon https://github.com/carbon-design-system/carbon/blob/main/packages/styles/scss/utilities/_z-index.scss

const above = 1;
const below = -1;

const zIncidentBanner = above + zLayoutBase;
const zOverlayCollapsable = below + zAppHeader;
const zNotificationContainer = above + zAppHeader;

export {zOverlayCollapsable, zIncidentBanner, zNotificationContainer};
