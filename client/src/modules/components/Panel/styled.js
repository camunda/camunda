import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Panel = themed(styled.div`
  position: relative;
  display: flex;
  flex-direction: column;
  width: 100%;
  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark04,
      light: Colors.uiLight05
    })};
  border-bottom: none;
  border-radius: ${props => (props.isRounded ? '3px 3px 0 0' : 0)};
  background-color: ${themeStyle({
    dark: Colors.uiDark02,
    light: Colors.uiLight04
  })};
`);
