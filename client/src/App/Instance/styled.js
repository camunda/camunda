import styled from 'styled-components';

import {HEADER_HEIGHT} from './../Header/styled';

export const Instance = styled.div`
  display: flex;
  flex-direction: column;
  height: calc(100vh - ${HEADER_HEIGHT}px);
`;

export const Top = styled.div`
  flex-grow: 1;
  display: flex;
`;

export const Bottom = styled.div`
  flex-grow: 1;
  display: flex;
`;
