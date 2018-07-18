import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Select = themed(styled.select`
  width: 100%;
  height: 26px;

  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark05,
      light: Colors.uiLight05
    })};
  border-radius: 3px;

  background-color: ${themeStyle({
    dark: '#3e3f45',
    light: Colors.uiLight03
  })};
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiDark06
  })};

  font-family: IBMPlexSans;
  font-size: 13px;

  box-shadow: 0 2px 2px 0 rgba(0, 0, 0, 0.08);

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
`);
