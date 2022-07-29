/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';
import {ReactComponent as DefaultClose} from 'modules/components/Icon/close.svg';
import {ReactComponent as DefaultCheck} from 'modules/components/Icon/check.svg';
import {ActionButtons} from 'modules/components/ActionButtons';

const iconStyle: ThemedInterpolationFunction = ({theme}) => {
  const colors = theme.colors.variables.icons;

  return css`
    width: 16px;
    height: 16px;
    object-fit: contain;
    color: ${colors.color};
  `;
};

const CloseIcon = styled(DefaultClose)`
  ${iconStyle}
`;

const CheckIcon = styled(DefaultCheck)`
  ${iconStyle}
`;

type ContainerProps = {
  className?: string;
};

const Container = styled(ActionButtons)<ContainerProps>`
  margin-top: 4px;
  min-width: 78px;
`;

export {CloseIcon, CheckIcon, Container};
