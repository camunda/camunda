import styled from 'styled-components';
import {Input} from 'modules/components';
import {Colors} from 'modules/theme';

export const H1 = styled.h1`
  align-self: center;
  width: 134px;
  height: 47px;
  opacity: 0.9;
  font-family: IBMPlexSans;
  font-size: 36px;
  font-weight: 500;
  font-style: normal;
  color: #ffffff;
`;

export const Login = styled.form`
  display: flex;
  flex-direction: column;
  margin: 128px auto 0 auto;
  width: 340px;
  font-family: IBMPlexSans;
`;

export const LoginInput = styled(Input)`
  margin-bottom: 10px;
`;

export const FormError = styled.span`
  font-family: IBMPlexSans;
  font-size: 15px;
  font-weight: 500;
  color: ${Colors.incidentsAndErrors};
  margin-bottom: 10px;
`;
