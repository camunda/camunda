import styled, {css} from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

const typeBasedCSS = ({type, theme}) => {
  const backgroundColor = themeStyle({
    dark: type === 'submit' ? Colors.uiDark04 : Colors.uiDark02,
    light: type === 'submit' ? Colors.uiLight03 : Colors.uiLight04
  })({theme});

  const color = themeStyle({
    dark: '#ffffff',
    light: type === 'submit' ? Colors.uiDark02 : '#7e7e7f'
  })({theme});

  return css`
    background-color: ${backgroundColor};
    color: ${color};
  `;
};
export const Input = themed(styled.input`
  font-family: IBMPlexSans;
  font-size: 15px;
  font-style: ${({type}) =>
    type === 'button' || type === 'text' ? 'italic' : 'normal'};
  border: solid 1px
    ${themeStyle({
      dark: Colors.uiDark05,
      light: Colors.uiLight05
    })};
  padding: 6px 12px;
  ${typeBasedCSS};
`);
