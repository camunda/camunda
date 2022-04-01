/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import styled, {css} from 'styled-components';

const Container = styled.section`
  ${({theme}) => {
    const colors = theme.colors.decisionInstance;

    return css`
      width: 100%;
      height: 100%;
      display: grid;
      grid-template-rows: 38px 1fr;
      background-color: ${colors.backgroundColor};
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

      font-family: IBM Plex Sans;
      font-size: 16px;
      font-weight: normal;
      color: ${colors.text01};
      padding: 0 4px;

      ${isSelected
        ? css`
            height: calc(100% - 3px);
            border-bottom: 3px solid ${colors.selections};
            font-weight: 600;
          `
        : css`
            height: 100%;
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
      border: 1px solid ${theme.colors.borderColor};
      background-color: ${colors.backgroundColor};
    `;
  }}
`;

export {Container, Header, Tab};
