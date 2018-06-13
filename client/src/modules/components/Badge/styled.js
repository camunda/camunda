import styled from 'styled-components';
import {Colors} from 'modules/theme';

export const Badge = styled.div`
  display: inline-block;
  height: 17px;
  line-height: 17px;
  border-radius: 8.5px;
  font-weight: 600;
  background-color: ${({type}) => {
    switch (type) {
      case 'filters':
        return Colors.filtersAndWarnings;
      case 'selections':
        return Colors.selections;
      case 'incidents':
        return Colors.incidentsAndErrors;
      default:
        return '#a4a2a2';
    }
  }};
  padding-left: 9px;
  padding-right: 9px;
  color: ${({type}) => {
    switch (type) {
      case 'filters':
        return Colors.uiDark01;
      default:
        return '#ffffff';
    }
  }};
  font-size: 12px;
  vertical-align: middle;
  margin-left: 6px;
`;
