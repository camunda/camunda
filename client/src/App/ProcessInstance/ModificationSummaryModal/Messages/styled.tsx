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
`;

const ErrorContainer = styled.div`
  ${({theme}) => {
    const colors =
      theme.colors.processInstance.modifications.summaryModal.error;

    return css`
      ${containerStyles};
      align-items: flex-start;
      background-color: ${colors.backgroundColor};
      color: ${colors.color};
      box-shadow: ${theme.shadows.modificationSummaryModal.error};
      padding: 14px 10px 15px;
    `;
  }}
`;
const WarningContainer = styled.div`
  ${({theme}) => {
    const colors =
      theme.colors.processInstance.modifications.summaryModal.warning;

    return css`
      ${containerStyles};
      align-items: center;
      background-color: ${colors.backgroundColor};
      color: ${colors.color};
      box-shadow: ${theme.shadows.modificationSummaryModal.warning};
    `;
  }}
`;

const iconStyles = css`
  margin: 0 15px;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
`;

const ErrorIcon = styled(ErrorIconBase)`
  ${iconStyles}
`;

const WarningIcon = styled(WarningIconBase)`
  ${({theme}) => {
    return css`
      ${iconStyles}
      color: ${theme.colors.filtersAndWarnings};
    `;
  }}
`;

const Text = styled.p`
  margin: 0;
  font-weight: 500;
`;

export {WarningContainer, ErrorContainer, ErrorIcon, WarningIcon, Text};
