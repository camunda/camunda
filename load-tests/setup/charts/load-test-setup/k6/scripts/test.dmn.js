import { check, sleep } from 'k6';

import * as auth from './lib.auth.js';
import * as helpers from './lib.helpers.js';
import * as camunda from './lib.camunda.js';

const DATA_DIR = __ENV.DATA_DIR || '/data';

export const options = {
  // Give enough time for Camunda to be ready before continuing with the test.
  setupTimeout: '15m',
  scenarios: {
    evaluateDMN: {
      exec: 'evaluateDMN',
      executor: 'ramping-arrival-rate',
      startRate: 2,
      timeUnit: '1s',
      preAllocatedVUs: 10,
      maxVUs: 300,

      stages: [
        { target: 2, duration: '5m' },
        { target: 10, duration: '30m' },
        { target: 20, duration: '30m' },
        { target: 50, duration: '60m' },
        { target: 100, duration: '60m' },
        { target: 200, duration: '60m' },
        { target: 100, duration: '60d' },
      ],
    },
  },
};


const DMN_RESOURCE_NAME = 'small_decision';

const DMN_FILENAME = `${DMN_RESOURCE_NAME}.dmn`;
const dmn = open(`${DATA_DIR}/${DMN_FILENAME}`);

export async function setup() {
  const context = await helpers.setupContext();

  // Deploy the DMN file and wait for the decision definition evaluation to be ready.
  while (true) {
    console.log(`Deploying DMN file: ${DMN_FILENAME}...`);
    try {
      await camunda.deploy(context, DMN_RESOURCE_NAME, dmn);
      console.log(`DMN file ${DMN_FILENAME} deployed successfully.`);
    } catch (e) {
      console.error(`Failed to deploy DMN file ${DMN_FILENAME}. Retrying in 5 seconds...`, e);
      sleep(5);
      continue;
    }

    console.log('Waiting for decision definition evaluation to be ready...');
    try {
      const response = await camunda.decisionDefinitionEvaluation(context, DMN_RESOURCE_NAME);
      if (response.status !== 200) {
        throw new Error(`Decision definition evaluation failed. Status: ${response.status}, Body: ${response.body}`);
      }

      console.log('Decision definition evaluation is ready.');
      break; // Exit the loop if successful
    }
    catch (e) {
      console.error('Decision definition evaluation failed. Retrying in 5 seconds...', e);
      sleep(5); // Wait for 5 seconds before retrying
    }
  }

  // The returned context object must be JSON serializable.
  return context;
}

export async function evaluateDMN(context) {
  auth.renew(context.token);

  const response = await camunda.decisionDefinitionEvaluation(context, DMN_RESOURCE_NAME, {});
  if (response.status !== 200) {
    console.error(`Decision definition evaluation failed. Status: ${response.status}, Body: ${response.body}`);
  }
  check(response, { 'status is 200': (r) => r.status === 200 });
};

