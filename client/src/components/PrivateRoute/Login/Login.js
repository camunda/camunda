/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useState, useEffect, useRef} from 'react';

import {MessageBox, Button, Input, Labeled} from 'components';
import {t} from 'translation';
import {isMetadataTelemetryEnabled} from 'config';

import {login} from './service';
import {ReactComponent as Logo} from './logo.svg';

import './Login.scss';

export default function Login({onLogin}) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [waitingForServer, setWaitingForServer] = useState(false);
  const [error, setError] = useState(null);
  const [telemetryEnabled, setTelemetryEnabled] = useState(null);

  const passwordField = useRef(null);

  useEffect(() => {
    isMetadataTelemetryEnabled().then(setTelemetryEnabled);
  }, []);

  const usernameLabel = t('login.username');
  const passwordLabel = t('login.password');

  async function submit(evt) {
    evt.preventDefault();

    setWaitingForServer(true);

    const authResult = await login(username, password);

    if (authResult.token) {
      onLogin(authResult.token);
    } else {
      const {errorCode, errorMessage} = authResult;
      const error = errorCode ? t('apiErrors.' + errorCode) : errorMessage;

      setWaitingForServer(false);
      setError(error || t('login.error'));

      if (passwordField.current) {
        passwordField.current.focus();
        passwordField.current.select();
      }
    }
  }

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
      {typeof telemetryEnabled === 'boolean' && (
        <div className="telemetryNotice">
          {t('login.telemetry')} <b>{t(telemetryEnabled ? 'common.enabled' : 'common.disabled')}</b>
          .
        </div>
      )}
    </form>
  );
}
