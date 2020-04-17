/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import {t} from 'translation';
import {login} from './service';
import {MessageBox, Button, Input, Labeled} from 'components';
import {ReactComponent as Logo} from './logo.svg';

import './Login.scss';

export default class Login extends React.Component {
  constructor(props) {
    super(props);

    this.state = {
      username: '',
      password: '',
      waitingForServer: false,
      error: null,
    };
  }

  handleInputChange = ({target: {name, value}}) => {
    this.setState({
      [name]: value,
    });
  };

  submit = async (evt) => {
    evt.preventDefault();

    const {username, password} = this.state;

    this.setState({waitingForServer: true});

    const authResult = await login(username, password);

    if (authResult.token) {
      this.props.onLogin(authResult.token);
    } else {
      const {errorCode, errorMessage} = authResult;
      const error = errorCode ? t('apiErrors.' + errorCode) : errorMessage;
      this.setState({waitingForServer: false, error: error || t('login.error')});
      this.passwordField.focus();
      this.passwordField.select();
    }
  };

  render() {
    const {username, password, waitingForServer, error} = this.state;
    const usernameLabel = t('login.username');
    const passwordLabel = t('login.password');

    return (
      <form className="Login">
        <Logo />
        <h1>{t('login.appName')}</h1>
        {error ? <MessageBox type="error">{error}</MessageBox> : ''}
        <div className="controls">
          <div className="row">
            <Labeled label={usernameLabel}>
              <Input
                type="text"
                placeholder={usernameLabel}
                value={username}
                onChange={this.handleInputChange}
                name="username"
                autoFocus={true}
              />
            </Labeled>
          </div>
          <div className="row">
            <Labeled label={passwordLabel}>
              <Input
                placeholder={passwordLabel}
                value={password}
                onChange={this.handleInputChange}
                type="password"
                name="password"
                ref={(input) => (this.passwordField = input)}
              />
            </Labeled>
          </div>
        </div>
        <Button main primary type="submit" onClick={this.submit} disabled={waitingForServer}>
          {t('login.btn')}
        </Button>
      </form>
    );
  }
}
