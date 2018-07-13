import styled from 'styled-components';
import {themed, themeStyle, Colors} from 'modules/theme';

import {RemoveItem} from 'modules/components/Icon';

export const Selection = themed(styled.div`
  border-radius: 3px;
  border-color: ${themeStyle({dark: Colors.uiDark05, light: Colors.uiLight05})}
  width: 442px;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
  background: ${themeStyle({dark: Colors.uiDark03, light: Colors.uiLight02})};
`);

export const Header = styled.div`
  padding-left: 9px;
  padding-right: 20px;
  border-radius: 3px 0 0 0;
  display: flex;
  align-items: center;

  font-size: 15px;
  font-weight: 600;

  height: 32px;
  background: ${({isOpen}) => isOpen && Colors.selections};
`;

export const Headline = styled.div`
  padding-left: 9px;
  padding-right: 7px;
`;

export const Actions = styled.div`
  display: flex;
  align-items: center;
  margin-left: auto;
`;

const separator = themeStyle({
  dark: 'rgba(246, 252, 251, 0.5)',
  light: 'rgba(98, 98, 110, 0.25)'
});

export const DropdownTrigger = styled.div`
  display: flex;
  padding: 0 10px;
  border-right: 1px solid ${separator};
  margin-right: 10px;
`;

export const DeleteIcon = styled(RemoveItem)`
  opacity: 0.45;
`;

export const Body = styled.div``;

export const Footer = styled.div`
  border-radius: 0 0 0 3px;
  background: ${Colors.selections};
`;
