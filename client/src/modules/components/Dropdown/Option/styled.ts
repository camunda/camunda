/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Option = styled.div`
  ${({theme}) => {
    const colors = theme.colors.modules.dropdown.option;

    return css`
      display: flex;
      align-items: center;
      height: 36px;
      width: 100%;
      text-align: left;
      font-size: 15px;
      font-weight: 600;
      line-height: 36px;

      &:not(:last-child) {
        border-bottom: 1px solid ${colors.borderColor};
      }
    `;
  }}
`;

const OptionButton = styled.button`
  ${({theme, disabled}) => {
    const colors = theme.colors.modules.dropdown.option.optionButton;

    return css`
      position: relative;
      display: flex;
      align-items: center;
      width: 100%;
      height: 100%;
      padding: 0 10px;
      border: none;
      background: none;
      color: ${disabled ? colors.disabled.color : colors.default.color};
      text-align: left;
      font-size: 15px;
      font-weight: 600;
      line-height: 36px;

      ${disabled
        ? ''
        : css`
            &:hover {
              background: ${colors.hover.backgroundColor};
            }

            &:active {
              background: ${theme.colors.menuActive};
            }
          `}
    `;
  }}
`;

export {Option, OptionButton};
