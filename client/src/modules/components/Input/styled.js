import styled from 'styled-components';
import {Colors} from 'theme';

export const Input = styled.input`
  font-family: IBMPlexSans;
  font-size: 15px;
  font-style: ${({type}) =>
    type === 'button' || type === 'submit' ? 'normal' : 'italic'};
  color: #ffffff;
  border: solid 1px #5b5e63;
  background-color: #313238;
  padding: 6px 12px;
`;

export const InputError = styled.span`
  font-family: IBMPlexSans;
  font-size: 15px;
  font-weight: 500;
  font-style: normal;
  font-stretch: normal;
  line-height: normal;
  letter-spacing: normal;
  color: ${Colors.incidentsAndErrors};
  margin-bottom: 10px;
`;
