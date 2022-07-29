/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled from 'styled-components';

import {Button as BaseButton} from 'modules/components/Button';
import {ReactComponent as DefaultPlus} from 'modules/components/Icon/plus.svg';

const Plus = styled(DefaultPlus)`
  height: 16px;
  margin-right: 4px;
`;

const Button = styled(BaseButton)`
  display: flex;
  justify-content: center;
  align-items: center;
  height: 22px;
  margin-left: 20px;
  padding: 0 10px;
`;

export {Button, Plus};
