/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';

const Overlay = styled.div`
  ${({theme}) => {
    const colors = theme.colors.incidentsOverlay;
    const image = theme.images.incidentsOverlay;

    return css`
      width: 100%;
      height: 100%;
      padding-left: 52px;
      position: absolute;
      z-index: 3;
      top: 0;
      bottom: 0;
      left: 0;
      right: 0;
      overflow: auto;
      background-image: ${image};
      background-repeat: repeat-y;
      background-position: fixed;
      background-color: ${colors.backgroundColor};
      background-size: 51px;
    `;
  }}
`;

export {Overlay};
