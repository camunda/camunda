import styled from 'styled-components';
import {HEADER_HEIGHT} from './../Header/styled';

export const Filter = styled.div`
  display: flex;
  flex-direction: row;
  height: calc(100vh - ${HEADER_HEIGHT}px);

  /* prevents header dropdown to not go under the content */
  /* display: flex has z-index as well */
  z-index: 0;
`;

export const Left = styled.div`
  display: flex;
  width: 320px;
`;

export const Right = styled.div`
  width: 100%;
  display: flex;
  flex-direction: column;
`;

export const Top = styled.div`
  flex-grow: 1;
  display: flex;
`;
export const Bottom = styled.div`
  flex-grow: 1;
  display: flex;
`;
