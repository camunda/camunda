import {select} from '@inquirer/prompts';
import versions from './c8_versions.json' with {type: 'json'};
import {spawn} from 'node:child_process';
import {object, string, safeParse} from 'valibot';

const IS_LOCAL = Boolean(process.env.LOCAL_TEST);

const VersionsSchema = object({8.6: string(), 8.7: string(), 8.8: string()});

const browser = await select({
  message: 'Select the browser to run the tests on:',
  choices: [
    {name: 'Chrome', value: 'chromium'},
    {name: 'Firefox', value: 'firefox'},
    {name: 'Edge', value: 'msedge'},
  ],
});

if (!safeParse(VersionsSchema, versions).success) {
  console.error('Invalid versions mapping');
  process.exit(1);
}

const {minorVersion, exactVersion} = await select({
  message: 'Select a Camunda version to run the tests on:',
  choices: Object.entries(versions).map(([minorVersion, exactVersion]) => ({
    name: `${minorVersion} - ${exactVersion}`,
    value: {minorVersion, exactVersion},
  })),
});

const baseURL = getBaseURL(minorVersion, IS_LOCAL);

const runMode = await select({
  message: 'Which mode would like to run Playwright:',
  choices: [
    {name: 'Headless', value: 'headless'},
    {name: 'Headed', value: 'headed'},
  ],
});

let args = ['run', 'test', '--', `--project=${browser}`];

if (runMode === 'headed') {
  args.push('--headed');
}

spawn('npm', args, {
  cwd: process.cwd(),
  env: {
    ...process.env,
    CLUSTER_VERSION: exactVersion,
    MINOR_VERSION: minorVersion,
    PLAYWRIGHT_BASE_URL: baseURL,
  },
  stdio: 'inherit',
});

function getBaseURL() {
  if (typeof process.env.PLAYWRIGHT_BASE_URL === 'string') {
    return process.env.PLAYWRIGHT_BASE_URL;
  }
  return 'http://localhost:8080';
}
