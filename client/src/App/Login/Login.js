import React from 'react';
import PropTypes from 'prop-types';
import {Redirect} from 'react-router-dom';

import {login} from './api';
import * as Styled from './styled';

import withSharedState from 'modules/components/withSharedState';

class Login extends React.Component {
  static propTypes = {
    location: PropTypes.object.isRequired,
    clearState: PropTypes.func.isRequired
  };

  state = {
    username: '',
    password: '',
    forceRedirect: false,
    error: null
  };

  handleLogin = async e => {
    e.preventDefault();
    const {username, password} = this.state;
    try {
      await login({username, password});
      this.props.clearState();
      this.setState({forceRedirect: true});
    } catch (e) {
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
      const locationState = this.props.location.state || {referrer: '/'};
      return <Redirect to={locationState.referrer} />;
    }

    // default render
    return (
      <Styled.Login onSubmit={this.handleLogin}>
        <Styled.LoginHeader>
          <Styled.Logo />
          <Styled.LoginTitle>Operate</Styled.LoginTitle>
        </Styled.LoginHeader>
        <Styled.LoginForm>
          {error && <Styled.FormError>{error}</Styled.FormError>}
          <Styled.UsernameInput
            value={username}
            type="text"
            onChange={this.handleInputChange}
            placeholder="User Name"
            name="username"
            required
          />
          <Styled.PasswordInput
            value={password}
            type="password"
            onChange={this.handleInputChange}
            placeholder="Password"
            name="password"
            required
          />
          <Styled.SubmitButton type="submit">Login</Styled.SubmitButton>
        </Styled.LoginForm>
      </Styled.Login>
    );
  }
}

export default withSharedState(Login);
