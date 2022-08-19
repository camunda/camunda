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
const zModal = above + zModificationFrame;
const zDateRangePopover = below + zNotification;
const zDataTableHeader = above + zLayoutBase;

export {
  zNotification,
  zModal,
  zModificationFrame,
  zDateRangePopover,
  zDataTableHeader,
};
