import styled from 'styled-components';
import operateTheme from './../operate-theme';

export const Header = styled.h1`
  font-size: 18px;
  color: ${props => operateTheme[props.theme].colors.primary};
`;
