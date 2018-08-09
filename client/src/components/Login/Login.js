import React from 'react';
import {Redirect} from 'react-router-dom';
import {getToken} from 'credentials';

import {login} from './service';

import {Logo, Message, Button, Input} from 'components';

import './Login.css';

export default class Login extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      username: '',
      password: '',
      error: null,
      redirect: false
    };
  }

  handleInputChange = ({target: {name, value}}) => {
    this.setState({
      [name]: value
    });
  };

  submit = async evt => {
    evt.preventDefault();

    const {username, password} = this.state;

    const authResult = await login(username, password);

    if (authResult.token) {
      this.setState({error: null, redirect: true});
    } else {
      const error = authResult.errorMessage || 'An error occurred. Could not log you in.';
      this.setState({error});
      this.passwordField.focus();
      this.passwordField.select();
    }
  };

  render() {
    const {username, password, redirect, error} = this.state;
    const locationState = this.props.location && this.props.location.state;

    if (redirect || getToken()) {
      return <Redirect to={(locationState && locationState.from) || '/'} />;
    }

    return (
      <form className="Login">
        <h1 className="Login__heading">
          <Logo className="Login__logo" />
          Camunda Optimize
        </h1>
        {error ? <Message type="error">{error}</Message> : ''}
        <div className="Login__controls">
          <div className="Login__row">
            <label className="Login__label" htmlFor="username">
              Username
            </label>
            <Input
              className="Login__input"
              type="text"
              placeholder="Username"
              value={username}
              onChange={this.handleInputChange}
              name="username"
              autoFocus={true}
            />
          </div>
          <div className="Login__row">
            <label className="Login__label" htmlFor="password">
              Password
            </label>
            <Input
              className="Login__input"
              placeholder="Password"
              value={password}
              onChange={this.handleInputChange}
              type="password"
              name="password"
              ref={input => (this.passwordField = input)}
            />
          </div>
        </div>
        <Button onClick={this.submit} type="primary" color="blue" className="Login__button">
          Login
        </Button>
      </form>
    );
  }
}
