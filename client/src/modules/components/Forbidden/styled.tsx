/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {EmptyState as BaseEmptyState} from 'modules/components/EmptyState';
import {ReactComponent as PermissionDenied} from 'modules/components/Icon/permission-denied.svg';

const EmptyState = styled(BaseEmptyState)`
  ${({theme}) => {
    return css`
      background-color: ${theme.colors.modules.forbidden.backgroundColor};
    `;
  }}
`;

export {EmptyState, PermissionDenied};
