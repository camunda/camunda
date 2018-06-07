import styled from 'styled-components';
import {Colors} from 'theme';

export const Header = styled.header`
  height: 56px;
  background-color: ${Colors.uiDark01};
  padding-top: 9px;
  padding-left: 20px;
  padding-right: 20px;
  font-size: 15px;
  font-weight: 600;
  color: #ffffff;
  line-height: 19px;
`;

export const DashboardLink = styled.span`
  padding-left: 20px;
  padding-right: 20px;
  border-right: 1px solid rgba(246, 252, 251, 0.5);
  display: inline-block;
  vertical-align: text-bottom;
  & > :first-child {
    ${({active}) => (active ? '' : 'opacity: 0.5;')};
  }
`;

export const ListLink = styled.span`
  margin-left: 20px;
  vertical-align: text-bottom;
  & > :first-child {
    ${({active}) => (active ? '' : 'opacity: 0.5;')};
  }
  & > :last-child {
    ${({active}) => (active ? '' : 'opacity: 0.8;')};
  }
`;

export const ProfileDropdown = styled.span`
  margin-right: 8px;
  vertical-align: text-bottom;
  float: right;
  opacity: 0.9;
`;
