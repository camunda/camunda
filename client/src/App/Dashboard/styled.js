import styled from 'styled-components';
import {themed, operateTheme} from 'theme';

export const Header = themed(styled.h1`
  font-size: 18px;
  color: ${({theme}) => operateTheme[theme].colors.primary};
`);
