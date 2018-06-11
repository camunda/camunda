import React from 'react';
import {Redirect} from 'react-router-dom';

import {login} from './api';
import {resetResponseInterceptor} from 'request';
import * as Styled from './styled';

class Login extends React.Component {
  constructor(props) {
    super(props);
    resetResponseInterceptor();
  }

  state = {
    username: '',
    password: '',
    forceRedirect: false,
    error: null
  };

  login = async e => {
    e.preventDefault();
    const {username, password} = this.state;
    try {
      await login({username, password});
      this.setState({forceRedirect: true});
    } catch (error) {
      this.setState({error: 'Username and Password do not match'});
    }
  };

  handleInputChange = ({target: {name, value}}) => {
    this.setState({[name]: value});
  };

  render() {
    const {username, password, forceRedirect, error} = this.state;
    // case of successful login
    if (forceRedirect) {
      return <Redirect to={'/'} />;
    }

    // default render
    return (
      <Styled.Login onSubmit={this.login}>
        <Styled.H1>Operate</Styled.H1>
        {error && <Styled.FormError>{error}</Styled.FormError>}
        <Styled.LoginInput
          value={username}
          onChange={this.handleInputChange}
          placeholder="Username"
          name="username"
          required
        />
        <Styled.LoginInput
          value={password}
          type="password"
          onChange={this.handleInputChange}
          placeholder="Password"
          name="password"
          required
        />
        <Styled.LoginInput type="submit" placeholder="Password" />
      </Styled.Login>
    );
  }
}

export default Login;
