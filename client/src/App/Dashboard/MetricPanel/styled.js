import styled from 'styled-components';
import {Colors, themed} from 'theme';

export const Panel = themed(styled.div`
  padding: 66px 0;
  border-radius: 3px;
  border: solid 1px
    ${({theme}) => (theme === 'dark' ? Colors.uiDark04 : Colors.uiLight05)};
  background-color: ${({theme}) =>
    theme === 'dark' ? Colors.uiDark02 : Colors.uiLight04};
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
        ${({theme}) => (theme === 'dark' ? Colors.uiDark05 : Colors.uiLight05)};
    }
  }
`);
