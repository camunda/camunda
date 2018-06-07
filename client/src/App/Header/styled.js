import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'theme';

const muteInactive = amount => ({active}) =>
  active ? '' : `opacity: ${amount}; font-weight: 500;`;

const separator = themeStyle({
  dark: 'rgba(246, 252, 251, 0.5)',
  light: 'rgba(98, 98, 110, 0.25)'
});

export const Header = themed(styled.header`
  height: 56px;
  background-color: ${themeStyle({
    dark: Colors.uiDark01,
    light: Colors.uiLight01
  })}
  padding-top: 9px;
  padding-left: 20px;
  padding-right: 20px;
  font-size: 15px;
  font-weight: 600;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })}
  line-height: 19px;
  & > span {
    vertical-align: text-bottom;
    display: inline-block;
  }
`);

export const DashboardLink = themed(styled.span`
  padding-left: 20px;
  padding-right: 20px;
  border-right: 1px solid ${separator};
  ${muteInactive(0.5)};
`);

export const ListLink = themed(styled.span`
  margin-left: 20px;
  & > :first-child {
    ${muteInactive(0.5)};
  }
  & > :last-child {
    opacity: ${themeStyle({
      dark: 0.8,
      light: 0.7
    })};
  }
`);

export const Detail = styled.span`
  padding-left: 20px;
  border-left: 1px solid ${separator};
  margin-left: 20px;
`;

export const ProfileDropdown = styled.span`
  margin-right: 8px;
  float: right;
  opacity: 0.9;
`;
