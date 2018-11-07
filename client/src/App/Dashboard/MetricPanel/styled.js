import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Panel = themed(styled.div`
  padding: 66px 0;
  border-radius: 3px;
  border: solid 1px
    ${themeStyle({dark: Colors.uiDark04, light: Colors.uiLight05})};
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight04
  })};
  box-shadow: ${themeStyle({
    dark: '0 3px 6px 0 #000000',
    light: '0 2px 3px 0 rgba(0, 0, 0, 0.1)'
  })};
`);

export const Ul = themed(styled.ul`
  padding: 0px;
  margin: 0px;
  list-style-type: none;
  display: flex;
  justify-content: space-around;
  & > li {
    width: 100%;
    &:not(:last-child) {
      border-right: solid 1px
        ${themeStyle({
          dark: Colors.uiDark05,
          light: Colors.uiLight05
        })};
    }
  }
`);
