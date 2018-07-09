import styled from 'styled-components';

export const Content = styled.div`
  /* prevents header dropdown to not go under the content */
  /* display: flex has z-index as well */
  z-index: 0;
`;
