/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

const zLayoutBase = 0;
const zLayoutTopBase = 10000;

const above = 1;
const below = -1;

const zNotification = above + zLayoutTopBase;
const zModificationFrame = above + zLayoutTopBase;
const zModificationLoadingOverlay = below + zModificationFrame;
const zModal = above + zModificationFrame;
const zDateRangePopover = below + zNotification;
const zIconButton = above + zLayoutBase;
const zDataTableHeader = above + zIconButton;
const zLicenseNote = above + zModificationLoadingOverlay;
const zUserDropdown = above + zModificationLoadingOverlay;

export {
  zNotification,
  zModal,
  zModificationFrame,
  zModificationLoadingOverlay,
  zDateRangePopover,
  zDataTableHeader,
  zIconButton,
  zLicenseNote,
  zUserDropdown,
};
