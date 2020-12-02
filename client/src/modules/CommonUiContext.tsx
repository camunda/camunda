/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {useTheme} from 'styled-components';
import {CmContext} from '@camunda-cloud/common-ui-react';

const CommonUiContext: React.FC = () => {
  const theme = useTheme();
  //@ts-expect-error
  return <CmContext theme={theme.cmTheme} />;
};

export {CommonUiContext};
