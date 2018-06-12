import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'theme';

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

export const TableHead = styled.thead`
  text-align: left;
  background-color: ${Colors.uiDark03};
`;

export const HeaderCell = styled.th`
  font-weight: 600;
  padding: 0 0 0 5px;

  &:not(:last-child):after {
    content: ' ';
    float: right;
    border-right: 1px solid rgba(255, 255, 255, 0.15);
    height: 31px;
    margin-top: 3px;
  }
`;

export const BodyCell = styled.td`
  padding: 0 0 0 5px;
`;

export const BodyRow = styled.tr`
  height: 37px;
  line-height: 37px;
  border: 1px solid ${Colors.uiDark04};

  &:nth-child(odd) {
    background-color: ${Colors.uiDark02};
  }

  &:nth-child(even) {
    background-color: #37383e;
  }
`;

export const HeaderRow = styled.tr`
  height: 37px;
  line-height: 37px;
  border: 1px solid ${Colors.uiDark04};
`;
