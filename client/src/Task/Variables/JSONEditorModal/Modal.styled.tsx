/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import styled, {css} from 'styled-components';
import {rgba} from 'polished';

const Container = styled.div`
  ${({theme}) => css`
    position: absolute;
    top: 0;
    left: 0;
    width: 100vw;
    height: 100vh;
    display: flex;
    justify-content: center;
    align-items: center;
    background-color: ${rgba(theme.colors.black, 0.5)};
  `}
`;

const Content = styled.div`
  ${({theme}) => {
    const {colors} = theme;

    return css`
      width: 80%;
      height: 90%;
      border: 1px solid ${colors.ui05};
      border-radius: 3px;
      box-shadow: 0 2px 2px 0 ${rgba(colors.black, 0.5)};
      background-color: ${colors.ui04};
      display: grid;
      grid-template-rows: 55px 1fr 63px;
    `;
  }}
`;

const Header = styled.header`
  ${({theme}) => css`
    display: flex;
    flex-direction: row;
    justify-content: space-between;
    align-items: center;
    padding: 0 20px;
    border-bottom: 1px solid ${theme.colors.ui05};
    border-radius: 3px 3px 0 0;
    font-weight: bold;
    font-size: 15px;
    color: ${theme.colors.ui06};
  `}
`;

const Footer = styled.footer`
  ${({theme}) => css`
    display: flex;
    flex-direction: row;
    justify-content: flex-end;
    align-items: center;
    padding: 0 20px;
    border-top: 1px solid ${theme.colors.ui05};
    border-radius: 0 0 3px 3px;

    & > *:last-child {
      margin-left: 15px;
    }
  `}
`;

export {Container, Content, Header, Footer};
