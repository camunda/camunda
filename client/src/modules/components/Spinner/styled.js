import styled from 'styled-components';
import {Colors, Animations, themed, themeStyle} from 'modules/theme';

export const Spinner = themed(styled.div`
  border-radius: 50%;
  width: 1em;
  height: 1em;

  position: relative;

  border: 3px solid
    ${themeStyle({
      dark: '#ffffff',
      light: Colors.badge02
    })};
  border-right-color: transparent;

  transform: translateZ(0);
  animation: ${Animations.Spinner} 0.65s infinite linear;
`);
