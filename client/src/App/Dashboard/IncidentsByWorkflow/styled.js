import styled from 'styled-components';
import {default as IncidentStatisticComponent} from './IncidentStatistic';

export const Ul = styled.ul`
  margin: 0;
  padding: 0;
  list-style: none;
`;
export const Li = styled.li`
  margin: 0 0 29px;
  padding: 0;
`;
export const VersionLi = styled.li`
  margin: 21px 0 0;
  padding: 0;
`;

export const IncidentStatistic = styled(IncidentStatisticComponent)`
  margin-left: 32px;
`;
