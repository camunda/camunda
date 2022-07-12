/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';

const Container = styled.div`
  position: relative;
`;

const Input = styled.input`
  ${({theme}) => {
    const colors = theme.colors.login.input;

    return css`
      width: 100%;
      height: 48px;
      padding: 21px 8px 8px 8px;

      border-radius: 3px;
      border: solid 1px ${colors.borderColor};
      ${styles.bodyShort02};
      color: ${theme.colors.text01};

      background-color: ${colors.backgroundColor};

      & + label {
        pointer-events: none;
        user-select: none;
        position: absolute;
        top: 0px;
        left: 0px;

        transform: translate(9px, 5px);

        ${styles.label01};
        color: ${colors.labelColor};

        transition: all ease-in-out 150ms;
      }

      &:placeholder-shown:not(:focus-within) + label {
        transform: translate(9px, 14px);
        ${styles.label02};
        font-style: italic;
      }

      &:focus-visible {
        outline: none;
        box-shadow: 0px 0px 0px 1px ${colors.focusInner},
          0px 0px 0px 4px ${theme.colors.focusOuter};
      }
    `;
  }}
`;

export {Container, Input};
