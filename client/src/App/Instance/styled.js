import styled from 'styled-components';

import {StateIconIncident} from 'modules/components/Icon';

import {HEADER_HEIGHT} from './../Header/styled';

export const Instance = styled.div`
  display: flex;
  flex-direction: column;
  height: calc(100vh - ${HEADER_HEIGHT}px);

  /* prevents header dropdown to not go under the content */
  /* display: flex has z-index as well */
  z-index: 0;
`;

export const Top = styled.div`
  flex-grow: 1;
  display: flex;
`;

export const Bottom = styled.div`
  flex-grow: 1;
  display: flex;
`;

export const IncidentIcon = styled(StateIconIncident)`
  position: relative;
  top: 3px;
  margin-right: 5px;
`;
