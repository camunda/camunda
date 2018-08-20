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
    cursor: not-allowed;

    background-color: ${themeStyle({
      dark: '#34353a',
      light: '#f1f2f5'
    })};
    border-color: ${themeStyle({
      dark: Colors.uiDark05
    })};
    color: ${themeStyle({
      dark: 'rgba(247, 248, 250, 0.5)',
      light: 'rgba(69, 70, 78, 0.5)'
    })};
    box-shadow: none;
  }
`;

const sizeStyle = ({size}) => {
  const mediumSizeStyle = css`
    height: 35px;
    width: 117px;

    font-size: 14px;
  `;

  const largeSizeStyle = css`
    height: 48px;
    width: 340px;

    font-size: 18px;
  `;

  return size === 'medium' ? mediumSizeStyle : largeSizeStyle;
};

export const Button = themed(styled.button`
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
  font-weight: 600;

  color: ${themeStyle({
    dark: Colors.uiLight02,
    light: 'rgba(69, 70, 78, 0.9)'
  })};
  background-color: ${themeStyle({
    dark: Colors.uiDark05,
    light: Colors.uiLight05
  })};

  cursor: pointer;

  ${hoverStyle};
  ${activeStyle};
  ${disabledStyle};
  ${sizeStyle};
`);
