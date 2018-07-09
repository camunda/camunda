import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

import DefaultSortIcon from './SortIcon';

export const Table = themed(styled.table`
  width: 100%;
  font-size: 14px;
  border-spacing: 0;
  border-collapse: collapse;
  color: ${themeStyle({
    dark: '#ffffff',
    light: Colors.uiLight06
  })};
`);

export const TableHead = themed(styled.thead`
  text-align: left;
  background-color: ${themeStyle({
    dark: Colors.uiDark03,
    light: Colors.uiLight02
  })};
`);

export const HeaderCell = themed(styled.th`
  font-weight: 600;
  padding: 0 0 0 5px;

  &:not(:last-child):after {
    content: ' ';
    float: right;
    border-right: 1px solid
      ${themeStyle({
        dark: 'rgba(255, 255, 255, 0.15)',
        light: 'rgba(28, 31, 35, 0.15)'
      })};
    height: 31px;
    margin-top: 3px;
  }
`);

export const SortIcon = styled(DefaultSortIcon)`
  position: relative;
  top: 2px;
  margin-left: 4px;
`;

export const BodyCell = styled.td`
  padding: 0 0 0 5px;
  white-space: nowrap;
`;

export const BodyRow = themed(styled.tr`
  height: 37px;
  line-height: 37px;

  border-width: 1px 0;
  border-style: solid;
  border-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight05
  })};

  &:nth-child(odd) {
    background-color: ${({theme, selected}) => {
      if (selected) {
        if (theme === 'dark') {
          return '#3a527d';
        } else {
          return '#bfd6fe';
        }
      } else {
        if (theme === 'dark') {
          return Colors.uiDark02;
        } else {
          return Colors.uiLight04;
        }
      }
    }};
  }

  &:nth-child(even) {
    background-color: ${({theme, selected}) => {
      if (selected) {
        if (theme === 'dark') {
          return '#3e5681';
        } else {
          return '#bdd4fd';
        }
      } else {
        if (theme === 'dark') {
          return '#37383e';
        } else {
          return '#f9fafc';
        }
      }
    }};
  }
`);

export const HeaderRow = themed(styled.tr`
  height: 37px;
  line-height: 37px;

  border-width: 1px 0;
  border-style: solid;
  border-color: ${themeStyle({
    dark: Colors.uiDark04,
    light: Colors.uiLight05
  })};
`);
