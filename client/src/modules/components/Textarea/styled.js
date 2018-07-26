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

export const Textarea = themed(styled.textarea`
  display: block;

  width: 100%;
  height: 52px;
  padding: 4px 13px 6px 8px;

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
    dark: 'rgba(255, 255, 255, 0.9)',
    light: Colors.uiDark03
  })};

  font-family: IBMPlexSans;
  font-size: 13px;

  resize: none;

  ${placeholderStyle};
`);
