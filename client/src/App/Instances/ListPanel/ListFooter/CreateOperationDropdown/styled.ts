/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css, ThemedInterpolationFunction} from 'styled-components';

const MENU_WIDTH = 165;
const MENU_RIGHT_OFFSET = 52;

type DropdownContainerProps = {
  dropdownWidth: number;
};

const DropdownContainer = styled.div<DropdownContainerProps>`
  ${({dropdownWidth}) => {
    return css`
      position: relative;
      width: fit-content;

      ul {
        max-width: ${MENU_WIDTH}px;
        min-width: ${MENU_WIDTH}px;
        position: absolute;
        bottom: 30px;
        ${dropdownWidth
          ? css`
              left: calc(${dropdownWidth}px - ${MENU_RIGHT_OFFSET}px);
            `
          : ''}
      }
    `;
  }}
`;

const dropdownButtonStyles: ThemedInterpolationFunction = ({theme}) => {
  const colors = theme.colors.createOperationDropdown.dropdownButtonStyles;

  return css`
    font-size: 13px;
    font-weight: 600;

    background-color: ${theme.colors.selections};
    color: ${colors.color};

    &:hover {
      background-color: ${theme.colors.primaryButton04};
    }

    &:active,
    &[data-button-open='true'] {
      background-color: ${theme.colors.primaryButton04};
    }

    height: 26px;
    border-radius: 13px;
    border: none;
    padding: 4px 11px 5px 11px;
  `;
};

export {DropdownContainer, dropdownButtonStyles};
