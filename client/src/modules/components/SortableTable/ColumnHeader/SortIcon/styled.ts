/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

import {ReactComponent as DefaultUp} from 'modules/components/Icon/up.svg';
import {ReactComponent as DefaultDown} from 'modules/components/Icon/down.svg';

const sortIconStyle = css`
  height: 16px;
  width: 16px;
`;

const Up = styled(DefaultUp)`
  ${sortIconStyle};
`;

const Down = styled(DefaultDown)`
  ${sortIconStyle};
`;

export {Up, Down};
