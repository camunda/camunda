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
      this.setState({error});
    }
  };

  handleInputChange = ({target: {name, value}}) => {
    this.setState({[name]: value});
  };

  render() {
    // case of successful login
    if (this.state.forceRedirect) {
      return <Redirect to={'/'} />;
    }

    // default render
    return (
      <Styled.Login onSubmit={this.login}>
        <Styled.H1>Operate</Styled.H1>
        <Styled.LoginInput
          value={this.state.username}
          onChange={this.handleInputChange}
          placeholder="Username"
          name="username"
          error={this.state.error}
          required
        />
        <Styled.LoginInput
          value={this.state.password}
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
