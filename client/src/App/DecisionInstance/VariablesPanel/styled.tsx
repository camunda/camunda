/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {styles} from '@carbon/elements';

const Container = styled.section`
  ${({theme}) => {
    const colors = theme.colors.decisionInstance;

    return css`
      width: 100%;
      height: 100%;
      display: grid;
      grid-template-rows: 37px 1fr;
      background-color: ${colors.backgroundColor};
      border-top: 1px solid ${theme.colors.borderColor};
    `;
  }}
`;

type TabsProps = {
  isSelected: boolean;
};

const Tab = styled.button<TabsProps>`
  ${({theme, isSelected}) => {
    const {colors} = theme;

    return css`
      all: unset;
      cursor: pointer;

      ${styles.productiveHeading02}
      color: ${colors.text01};
      padding: 0 4px;

      ${isSelected
        ? css`
            height: calc(100% - 3px);
            border-bottom: 3px solid ${colors.selections};
          `
        : css`
            height: 100%;
            font-weight: normal;
          `}

      &:not(:last-child) {
        margin-right: 20px;
      }
    `;
  }}
`;

const Header = styled.header`
  ${({theme}) => {
    const colors = theme.colors.decisionInstance.panelHeader;

    return css`
      padding: 0 20px;
      border-width: 0 1px 1px 1px;
      border-style: solid;
      border-color: ${theme.colors.borderColor};
      background-color: ${colors.backgroundColor};
    `;
  }}
`;

export {Container, Header, Tab};
