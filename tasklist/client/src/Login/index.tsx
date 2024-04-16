/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */

import {useLocation, useNavigate, Location} from 'react-router-dom';
import {Form, Field} from 'react-final-form';
import {FORM_ERROR} from 'final-form';
import {authenticationStore} from 'modules/stores/authentication';
import {pages} from 'modules/routing';
import {getCurrentCopyrightNoticeText} from 'modules/utils/getCurrentCopyrightNoticeText';
import {Disclaimer} from './Disclaimer';
import {
  TextInput,
  PasswordInput,
  Column,
  Grid,
  Stack,
  InlineNotification,
  Button,
} from '@carbon/react';
import {LoadingSpinner} from './LoadingSpinner';
import {CamundaLogo} from 'modules/components/CamundaLogo';
import {z} from 'zod';
import styles from './styles.module.scss';

const locationStateSchema = z.object({
  referrer: z.object({
    pathname: z.string(),
    search: z.string(),
  }),
});

function stateHasReferrer(state: unknown): state is {referrer: Location} {
  const {success} = locationStateSchema.safeParse(state);

  return success;
}

type FormValues = {
  username: string;
  password: string;
};

const Login: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const {handleLogin} = authenticationStore;

  return (
    <Grid as="main" condensed className={styles.container}>
      <Form<FormValues>
        onSubmit={async ({username, password}) => {
          try {
            const {error} = await handleLogin(username, password);

            if (error === null) {
              return navigate(
                stateHasReferrer(location.state)
                  ? location.state.referrer
                  : {...location, pathname: pages.initial},
                {
                  replace: true,
                },
              );
            }

            if (error.response?.status === 401) {
              return {
                [FORM_ERROR]: 'Username and password do not match',
              };
            }

            return {
              [FORM_ERROR]: 'Credentials could not be verified',
            };
          } catch {
            return {
              [FORM_ERROR]: 'Credentials could not be verified',
            };
          }
        }}
        validate={({username, password}) => {
          const errors: {username?: string; password?: string} = {};

          if (!username) {
            errors.username = 'Username is required';
          }

          if (!password) {
            errors.password = 'Password is required';
          }

          return errors;
        }}
      >
        {({handleSubmit, submitError, submitting}) => {
          return (
            <Column
              as="form"
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
              onSubmit={handleSubmit}
            >
              <Stack>
                <div className={styles.logo}>
                  <CamundaLogo aria-label="Camunda logo" />
                </div>
                <h1 className={styles.title}>Tasklist</h1>
              </Stack>
              <Stack gap={3}>
                <span className={styles.error}>
                  {submitError && (
                    <InlineNotification
                      title={submitError}
                      hideCloseButton
                      kind="error"
                      role="alert"
                    />
                  )}
                </span>
                <div className={styles.field}>
                  <Field<FormValues['username']> name="username" type="text">
                    {({input, meta}) => (
                      <TextInput
                        {...input}
                        name={input.name}
                        id={input.name}
                        onChange={input.onChange}
                        labelText="Username"
                        invalid={meta.error && meta.touched}
                        invalidText={meta.error}
                        placeholder="Username"
                      />
                    )}
                  </Field>
                </div>
                <div className={styles.field}>
                  <Field<FormValues['password']>
                    name="password"
                    type="password"
                  >
                    {({input, meta}) => (
                      <PasswordInput
                        {...input}
                        name={input.name}
                        id={input.name}
                        onChange={input.onChange}
                        hidePasswordLabel="Hide password"
                        showPasswordLabel="Show password"
                        labelText="Password"
                        invalid={meta.error && meta.touched}
                        invalidText={meta.error}
                        placeholder="Password"
                      />
                    )}
                  </Field>
                </div>
                <Button
                  type="submit"
                  disabled={submitting}
                  renderIcon={submitting ? LoadingSpinner : undefined}
                  className={styles.button}
                >
                  {submitting ? 'Logging in' : 'Login'}
                </Button>
                <Disclaimer />
              </Stack>
            </Column>
          );
        }}
      </Form>
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

Login.displayName = 'Login';

export {Login as Component};
