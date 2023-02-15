/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {ReactComponent as ErrorIconBase} from 'modules/components/Icon/notification-icon-error.svg';
import {ReactComponent as WarningIconBase} from 'modules/components/Icon/warning.svg';

const containerStyles = css`
  display: flex;
  margin-bottom: 19px;
  padding: 10px;
  border-radius: 3px;
  align-items: center;
`;

const ErrorContainer = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.messages.error;
    return css`
      ${containerStyles};
      background-color: ${colors.backgroundColor};
      color: ${colors.color};
      box-shadow: ${theme.shadows.modules.messages.error};
      padding: 14px 10px 15px;
    `;
  }}
`;
const WarningContainer = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.messages.warning;
    return css`
      ${containerStyles};
      background-color: ${colors.backgroundColor};
      color: ${colors.color};
      box-shadow: ${theme.shadows.modules.messages.warning};
    `;
  }}
`;

const iconStyles = css`
  margin: 0 15px;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  align-self: flex-start;
`;

const ErrorIcon = styled(ErrorIconBase)`
  ${iconStyles}
`;

const WarningIcon = styled(WarningIconBase)`
  ${({theme}) => {
    return css`
      ${iconStyles};
      color: ${theme.colors.filtersAndWarnings};
    `;
  }}
`;

export {WarningContainer, ErrorContainer, ErrorIcon, WarningIcon};
