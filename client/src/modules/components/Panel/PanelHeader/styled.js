import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Header = themed(styled.div`
  display: flex;
  justify-content: space-between;
  flex-direction: row;
  padding: 3px;
  padding-left: 20px;
  background-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight02
  })};
  opacity: 0.9;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
  font-size: 15px;
  font-weight: bold;
  padding: 9px 10px;
  border-radius: ${props => (props.isRounded ? '3px 3px 0 0' : 0)};
  height: 38px;
`);

export const Headline = themed(styled.span``);
