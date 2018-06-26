import styled, {css} from 'styled-components';

import {Colors, themed, themeStyle} from 'modules/theme';

const placeholderStyle = css`
  &::placeholder {
    color: ${themeStyle({
      light: Colors.uiLight06
    })};
    font-style: italic;
  }
`;
export const Input = themed(styled.input`
  font-family: IBMPlexSans;
  font-size: 13px;

  height: 26px;
  padding-left: 8px;
  padding-right: 11px;
  padding-top: 4px;
  padding-bottom: 5px;
  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark05,
      light: Colors.uiLight05
    })};
  border-radius: 3px;

  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight04
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiDark03
  })};

  ${placeholderStyle};
`);
