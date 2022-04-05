/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {useTheme} from 'styled-components';
import {CmContext} from '@camunda-cloud/common-ui-react';

const CommonUiContext: React.FC = () => {
  const theme = useTheme();

  return <CmContext theme={theme.cmTheme} />;
};

export {CommonUiContext};
