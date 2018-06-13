import styled from 'styled-components';

export const Input = styled.input`
  font-family: IBMPlexSans;
  font-size: 15px;
  font-style: ${({type}) =>
    type === 'button' || type === 'text' ? 'italic' : 'normal'};
  color: #ffffff;
  border: solid 1px #5b5e63;
  background-color: #313238;
  padding: 6px 12px;
`;
