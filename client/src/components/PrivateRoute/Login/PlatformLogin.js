/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useState, useRef} from 'react';
import {
  Form,
  TextInput,
  PasswordInput,
  Button,
  Stack,
  InlineNotification,
  Grid,
  Column,
} from '@carbon/react';

import {PageTitle} from 'components';
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
      ({message}) => setError(message || t('login.errorMessage'))
    );

    setWaitingForServer(false);

    if (passwordField.current) {
      passwordField.current.focus();
      passwordField.current.select();
    }
  }

  return (
    <Form className="PlatformLogin">
      <PageTitle pageName={t('login.label')} />
      <Grid>
        <Column
          sm={4}
          md={{
            start: 3,
            end: 7,
          }}
          lg={{
            start: 7,
            end: 11,
          }}
        >
          <div className="header">
            <Logo />
            <h1>{t('login.appName')}</h1>
          </div>
          <Stack gap={8}>
            {error && (
              <InlineNotification
                kind="error"
                aria-label={t('login.closeError')}
                statusIconDescription={t('common.error')}
                onCloseButtonClick={() => setError(null)}
                subtitle={error}
              />
            )}
            <TextInput
              type="text"
              placeholder={usernameLabel}
              labelText={usernameLabel}
              value={username}
              onChange={(evt) => setUsername(evt.target.value)}
              name="username"
              id="loginUserName"
              autoFocus={true}
            />
            <PasswordInput
              placeholder={passwordLabel}
              labelText={passwordLabel}
              value={password}
              onChange={(evt) => setPassword(evt.target.value)}
              name="password"
              id="loginPassword"
              ref={passwordField}
            />
            <Button type="submit" onClick={submit} disabled={waitingForServer}>
              {t('login.btn')}
            </Button>
            <div className="privacyNotice">{t('login.telemetry')}</div>
          </Stack>
        </Column>
      </Grid>
    </Form>
  );
}

export default withErrorHandling(PlatformLogin);
