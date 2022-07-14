/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {StatusMessage} from 'modules/components/StatusMessage';
import {styles} from '@carbon/elements';

const Grid = styled.main`
  ${({theme}) => {
    const colors = theme.colors.dashboard;
    return css`
      width: 100%;
      height: 100%;
      padding: 20px 20px 10px;
      background-color: ${colors.backgroundColor};
      display: grid;
      grid-template-columns: calc(50% - 4px) calc(50% - 4px);
      grid-template-rows: 176px 1fr;
      grid-gap: 8px;
      & > ${Tile}:first-of-type {
        grid-column-start: 1;
        grid-column-end: 3;
      }
    `;
  }}
`;

const Tile = styled.div`
  ${({theme}) => {
    const colors = theme.colors.dashboard.panelStyles;
    const shadow = theme.shadows.dashboard.panelStyles;
    return css`
      width: 100%;
      height: 100%;
      padding: 24px 24px 40px 28px;
      border-radius: 3px;
      &:first-of-type {
        padding: 30px 104px;
      }
      border: solid 1px ${theme.colors.borderColor};
      background-color: ${colors.backgroundColor};
      box-shadow: ${shadow};
      display: flex;
      flex-direction: column;
      overflow: auto;

      & > ${StatusMessage} {
        margin-top: 37px;
      }
    `;
  }}
`;

const TileTitle = styled.h2`
  ${({theme}) => {
    const opacity = theme.opacity.dashboard.tileTitle;
    return css`
      ${styles.productiveHeading02};
      margin: 0 0 14px;
      padding: 0;
      color: ${theme.colors.text02};
      opacity: ${opacity};
    `;
  }}
`;

const TileContent = styled.div`
  overflow-y: auto;
  height: 100%;
  /* these styles are required to fully display focus borders */
  padding-left: 4px;
  margin-left: -4px;
  padding-top: 4px;
  margin-top: -4px;
  ${StatusMessage} {
    margin-top: 207px;
  }
`;

export {Grid, Tile, TileTitle, TileContent};
