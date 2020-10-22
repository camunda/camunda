/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const OptionButton = styled.button`
  ${({theme}) => {
    const colors = theme.colors.modules.dropdown.subOption.optionButton;

    return css`
      position: relative;
      height: 36px;
      display: flex;
      align-items: center;
      width: 100%;
      padding: 0 10px;
      border: none;
      background: none;
      color: ${colors.color};
      text-align: left;
      font-size: 15px;
      font-weight: 600;
      line-height: 36px;

      &:hover {
        background: ${colors.hover.backgroundColor};
      }

      &:active {
        background: ${theme.colors.active};
      }
    `;
  }}
`;

export {OptionButton};
