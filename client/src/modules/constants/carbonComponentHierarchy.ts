/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const zLayoutBase = 0;
const zAppHeader = 8000; // defined by Carbon https://github.com/carbon-design-system/carbon/blob/main/packages/styles/scss/utilities/_z-index.scss

const above = 1;
const below = -1;

const zIncidentBanner = above + zLayoutBase;
const zOverlayCollapsable = below + zAppHeader;

export {zOverlayCollapsable, zIncidentBanner};
