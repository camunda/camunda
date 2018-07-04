import styled from 'styled-components';
import {Colors} from 'modules/theme';

export const Badge = styled.div`
  display: inline-block;
  height: 17px;
  padding-left: 9px;
  padding-right: 9px;
  margin-left: 6px;

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
  color: ${({type}) => {
    switch (type) {
      case 'filters':
        return Colors.uiDark01;
      default:
        return '#ffffff';
    }
  }};

  font-size: 12px;
  font-weight: 600;
  line-height: 17px;

  border-radius: 8.5px;
`;
