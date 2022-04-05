/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

import {ReactComponent as UpBar} from 'modules/components/Icon/up-bar.svg';
import {ReactComponent as DownBar} from 'modules/components/Icon/down-bar.svg';
import {ReactComponent as LeftBar} from 'modules/components/Icon/left-bar.svg';
import {ReactComponent as RightBar} from 'modules/components/Icon/right-bar.svg';

const Up = styled(UpBar)``;

const Down = styled(DownBar)``;

const Left = styled(LeftBar)``;

const Right = styled(RightBar)``;

const CollapseButton = styled.button`
  ${({theme}) => {
    const opacity = theme.opacity.modules.collapseButton;

    return css`
      margin: 2px 3px;
      display: flex;
      justify-content: center;
      align-items: center;
      width: 39px;
      height: 32px;
      background: transparent;
      border: solid 1px ${theme.colors.borderColor};

      ${Up}, ${Down}, ${Left}, ${Right} {
        width: 16px;
        height: 16px;
        object-fit: contain;
        opacity: ${opacity.icons.default};
        color: ${theme.colors.text02};

        &:hover {
          opacity: ${opacity.icons.hover};
        }

        &:active {
          opacity: 1;
          color: ${theme.colors.text01};
        }
      }
    `;
  }}
`;

export {CollapseButton, Up, Down, Left, Right};
