import styled, {css} from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

const hoverStyle = css`
  &:hover {
    background-color: ${themeStyle({
      dark: '#6b6f74',
      light: '#cdd4df'
    })};
    border-color: ${themeStyle({
      dark: '#7f8289',
      light: '#9ea9b7'
    })};
  }
`;

const activeStyle = css`
  &:active {
    background-color: ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight03
    })};
    border-color: ${themeStyle({
      dark: Colors.uiDark05,
      light: '#88889a'
    })};
  }
`;

const disabledStyle = css`
  &:disabled {
    background-color: ${themeStyle({
      dark: '#34353a',
      light: '#f1f2f5'
    })};
    border-color: ${themeStyle({
      dark: Colors.uiDark05
    })};
    color: ${themeStyle({
      dark: Colors.uiLight02,
      light: Colors.uiDark04
    })};
  }
`;

export const Button = themed(styled.button`
  height: 35px;
  padding: 8px;
  padding-top: 9px;
  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark06,
      light: Colors.uiLight03
    })};
  border-radius: 3px;
  box-shadow: 0 2px 2px 0
    ${themeStyle({
      dark: 'rgba(0, 0, 0, 0.35)',
      light: 'rgba(0, 0, 0, 0.08)'
    })};

  font-family: IBMPlexSans;
  font-size: 14px;

  color: ${themeStyle({
    dark: Colors.uiLight02,
    light: Colors.uiDark04
  })};
  background-color: ${themeStyle({
    dark: Colors.uiDark05,
    light: Colors.uiLight05
  })};

  cursor: pointer;

  ${hoverStyle};
  ${activeStyle};
  ${disabledStyle};
`);
