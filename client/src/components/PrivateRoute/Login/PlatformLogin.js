/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useRef} from 'react';

import {MessageBox, Button, Input, Labeled, PageTitle} from 'components';
import {withErrorHandling} from 'HOC';
import {t} from 'translation';

import {login} from './service';
import {ReactComponent as Logo} from './logo.svg';

import './PlatformLogin.scss';

export function PlatformLogin({onLogin, mightFail}) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [waitingForServer, setWaitingForServer] = useState(false);
  const [error, setError] = useState(null);

  const passwordField = useRef(null);

  const usernameLabel = t('login.username');
  const passwordLabel = t('login.password');

  async function submit(evt) {
    evt.preventDefault();

    setWaitingForServer(true);

    await mightFail(
      login(username, password),
      async (token) => {
        if (token) {
          await onLogin(token);
        }
      },
      ({message}) => setError(message || t('login.error'))
    );

    setWaitingForServer(false);

    if (passwordField.current) {
      passwordField.current.focus();
      passwordField.current.select();
    }
  }

  return (
    <form className="PlatformLogin">
      <PageTitle pageName={t('login.label')} />
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
              onChange={(evt) => setUsername(evt.target.value)}
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
              onChange={(evt) => setPassword(evt.target.value)}
              type="password"
              name="password"
              ref={passwordField}
            />
          </Labeled>
        </div>
      </div>
      <Button main primary type="submit" onClick={submit} disabled={waitingForServer}>
        {t('login.btn')}
      </Button>
      <div className="privacyNotice">{t('login.telemetry')}</div>
    </form>
  );
}

export default withErrorHandling(PlatformLogin);
