import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const HEADER_HEIGHT = 56;
const separator = themeStyle({
  dark: 'rgba(246, 252, 251, 0.5)',
  light: 'rgba(98, 98, 110, 0.25)'
});

export const Header = themed(styled.header`
  height: ${HEADER_HEIGHT}px;
  background-color: ${themeStyle({
    dark: Colors.uiDark01,
    light: Colors.uiLight01
  })}
  padding: 9px 20px 0 20px;
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

  /* prevents user dropdown for going under content */
  /* each page content, with display: flex; should have a smaller z-index */
  position: relative;
  z-index: 2;
`);

export const DashboardLink = themed(styled.span`
  padding: 0 20px;
  border-right: 1px solid ${separator};
  ${({active}) => (active ? '' : `opacity: 0.5; font-weight: 500`)};
`);

export const ListLink = themed(styled.span`
  margin-left: 20px;
  & > :first-child {
    ${({active}) => (active ? '' : `opacity: 0.5; font-weight: 500`)};
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
  margin-left: 20px;
  border-left: 1px solid ${separator};
`;

export const ProfileDropdown = styled.span`
  margin-right: 8px;
  float: right;
  opacity: 0.9;
`;
