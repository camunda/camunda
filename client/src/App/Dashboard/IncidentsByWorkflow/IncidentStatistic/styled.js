import styled from 'styled-components';
import {Colors, themed, themeStyle} from 'modules/theme';

export const Wrapper = themed(styled.div`
  display: flex;
  padding: 0;
  list-type: none;

  color: ${themeStyle({
    dark: '#fff',
    light: Colors.uiLight06
  })};
  opacity: ${themeStyle({
    dark: 0.9,
    light: 1
  })};

  font-family: IBMPlexSans;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.71;
`);

export const IncidentsCount = themed(styled.div`
  width: 96px;

  color: ${Colors.incidentsAndErrors};
`);
export const Label = themed(styled.div``);

export const ActiveCount = themed(styled.div`
  margin-left: auto
  width: 139px;
  text-align: right;

  color: ${Colors.allIsWell};
  opacity: 0.8;
`);

export const IncidentStatisticBar = styled.div`
  display: flex;
  height: 3px;
  align-items: stretch;
  background: ${Colors.allIsWell};
  opacity: 0.8;
`;

export const IncidentsBar = styled.div`
  min-width: 1px;

  background: ${Colors.incidentsAndErrors};
`;
