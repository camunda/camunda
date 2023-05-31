/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Screen, UserEvent} from 'modules/testing-library';

const removeOptionalFilter = async ({
  label,
  user,
  screen,
}: {
  label: string;
  user: UserEvent;
  screen: Screen;
}) => {
  await user.hover(screen.getByLabelText(label));
  await user.click(screen.getByLabelText(`Remove ${label} Filter`));
};

export {removeOptionalFilter};
