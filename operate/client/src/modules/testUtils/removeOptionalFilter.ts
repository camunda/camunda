/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
