/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Form, useNavigation, useActionData, json, redirect,type ClientActionFunction} from '@remix-run/react';

import {getCurrentCopyrightNoticeText} from '~/utils/getCurrentCopyrightNoticeText';
import {Disclaimer} from '~/components/Disclaimer';
import {
  TextInput,
  PasswordInput,
  Column,
  Grid,
  Stack,
  InlineNotification,
  Button,
} from '@carbon/react';
import {LoadingSpinner} from '~/components/LoadingSpinner';
import {CamundaLogo} from '~/components/CamundaLogo';
import {z} from 'zod';
import styles from '~/styles/login.module.scss';

const formSchema = z.object({
  username: z
    .string({message: 'Username is required'})
    .min(1, 'Username is required'),
  password: z
    .string({message: 'Password is required'})
    .min(1, 'Password is required'),
});

const login = (body: z.infer<typeof formSchema>, request: Request) =>
  new Request(new URL('/api/login', request.url), {
    credentials: 'include',
    mode: 'cors',
    method: 'POST',
    body: new URLSearchParams(body).toString(),
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
  });

export const clientAction: ClientActionFunction = async ({request}) => {
  const formData = formSchema.safeParse(
    Object.fromEntries((await request.formData()).entries()),
  );

  if (formData.error !== undefined) {
    return json({
      error: {
        submitError: null,
        fields: formData.error.flatten().fieldErrors,
      },
    });
  }

  try {
    const response = await fetch(login(formData.data, request));

    if (response.ok) {
      return redirect('/tasklist', {
        status: 302,
        headers: response.headers,
      });
    }

    return json({
      error: {
        submitError: 'Username and password do not match',
        fields: null,
      },
    });
  } catch (error) {
    return json({
      error: {
        submitError: 'Credentials could not be verified',
        fields: null,
      },
    });
  }
};

const Login: React.FC = () => {
  const navigation = useNavigation();
  const data = useActionData<typeof clientAction>();
  const isSubmitting = navigation.state === 'submitting';

  return (
    <Grid as="main" condensed className={styles.container}>
      <Column
        as={Form}
        method="post"
        sm={4}
        md={{
          span: 4,
          offset: 2,
        }}
        lg={{
          span: 6,
          offset: 5,
        }}
        xlg={{
          span: 4,
          offset: 6,
        }}
      >
        <Stack>
          <div className={styles.logo}>
            <CamundaLogo aria-label="Camunda logo" />
          </div>
        </Stack>
        <Stack gap={3}>
          <span className={styles.error}>
            {data?.error?.submitError && (
              <InlineNotification
                title={data.error.submitError}
                hideCloseButton
                kind="error"
                role="alert"
              />
            )}
          </span>
          <div className={styles.field}>
            <TextInput
              name="username"
              id="username"
              labelText="Username"
              invalid={data?.error?.fields?.username !== undefined}
              invalidText={data?.error?.fields?.username}
              placeholder="Username"
            />
          </div>
          <div className={styles.field}>
            <PasswordInput
              name="password"
              id="password"
              hidePasswordLabel="Hide password"
              showPasswordLabel="Show password"
              labelText="Password"
              invalid={data?.error?.fields?.password !== undefined}
              invalidText={data?.error?.fields?.password}
              placeholder="Password"
            />
          </div>
          <Button
            type="submit"
            disabled={isSubmitting}
            renderIcon={isSubmitting ? LoadingSpinner : undefined}
            className={styles.button}
          >
            {isSubmitting ? 'Logging in' : 'Login'}
          </Button>
          <Disclaimer />
        </Stack>
      </Column>
      <Column
        sm={4}
        md={8}
        lg={16}
        as="span"
        className={styles.copyrightNotice}
      >
        {getCurrentCopyrightNoticeText()}
      </Column>
    </Grid>
  );
};

export default Login;
