/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/* istanbul ignore file */

import React from 'react';
import {IconButton as OriginalIconButton} from './index';
import {ReactComponent as PlusIcon} from 'modules/icons/plus.svg';
import styled from 'styled-components';

const Icon = styled(PlusIcon)`
  color: ${({theme}) => theme.colors.ui07};
  opacity: 0.9;
  margin-top: 4px;
`;

export default {
  title: 'Components/Modules',
  component: OriginalIconButton,
};

const IconButton: React.FC = () => {
  return (
    <OriginalIconButton>
      <Icon />
    </OriginalIconButton>
  );
};

export {IconButton};
