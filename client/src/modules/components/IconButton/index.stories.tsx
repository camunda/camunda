/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {IconButton} from './index';
import {ReactComponent as PlusIcon} from 'modules/icons/plus.svg';
import styled from 'styled-components';

const Icon = styled(PlusIcon)`
  color: ${({theme}) => theme.colors.ui07};
  opacity: 0.9;
  margin-top: 4px;
`;

export default {
  title: 'IconButton',
  component: IconButton,
};

const Default: React.FC = () => {
  return (
    <IconButton>
      <Icon />
    </IconButton>
  );
};

export {Default};
