import styled from 'styled-components';
import {themed, themeStyle, Colors} from 'modules/theme';

export const Selection = themed(styled.div`
  border-radius: 3px;
  color: ${themeStyle({dark: '#ffffff', light: Colors.uiLight06})};
`);

export const Header = styled.div`
  display: flex;
  justify-content: space-around;
  height: 32px;
  background: ${Colors.selections};
`;

export const Body = styled.div``;

export const Footer = styled.div`
  background: ${Colors.selections};
`;
