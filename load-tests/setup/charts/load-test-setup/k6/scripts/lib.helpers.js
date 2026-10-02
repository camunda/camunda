/* Helper functions for the k6 load test scripts. */
import { sleep } from 'k6';
import * as auth from './lib.auth.js';
import * as camunda from './lib.camunda.js';

export function ensureEnvVars() {
  const vars = [
      "CAMUNDA_BASE_URL",
      "CAMUNDA_CLIENT_ID",
      "CAMUNDA_CLIENT_SECRET",
      "CAMUNDA_OAUTH_URL",
      "CAMUNDA_TOKEN_AUDIENCE",
  ];

  let missing = [];

  for (let i = 0; i < vars.length; i++) {
    const name = vars[i];
    const value = __ENV[name];
    if (value === undefined || value === '') {
      missing.push(name);
      console.error(`Environment variable ${name} is not defined or empty.`);
    }
  }

  if (missing.length > 0) {
    throw Error(`Missing environment variables: ${missing.join(', ')}`);
  }
}

export async function waitCamundaReady(context) {
  const waitTime = 10; // seconds
  while (true) {
    try {
      console.info("Trying to get the first token...");
      auth.renew(context.token);
      console.info("Got a token, checking if Camunda topology is ready.");

      const response = await camunda.topology(context);
      if (response.status !== 200) {
        throw new Error(`Camunda topology endpoint returned status: ${response.status}`);
      }
      console.info("Got a token and Camunda topology looks good!")
      break;
    } catch (e) {
      console.error(`Error waiting for Camunda to be ready (will retry in ${waitTime}s).`, e);
      sleep(waitTime);
    }
  }
}

/* Common `setup()` body: validate the environment, authenticate, and wait for Camunda.
 * Returns the context passed to every scenario function. */
export async function setupContext() {
  ensureEnvVars();

  const token = auth.createToken();
  const context = {
    token: token,
    baseURL: __ENV.CAMUNDA_BASE_URL,
  };

  await waitCamundaReady(context);
  console.log("Camunda seems to be ready ✅");

  return context;
}
