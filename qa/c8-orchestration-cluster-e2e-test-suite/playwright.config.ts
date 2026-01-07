/* eslint-disable */
import {devices, defineConfig} from '@playwright/test';
import * as dotenv from 'dotenv';

dotenv.config();

const testRailOptions = {
  embedAnnotationsAsProperties: true,
  outputFile: './test-results/junit-report.xml',
};

// Define reporters without SlackReporter
const useReportersWithoutSlack: any[] = [
  ['list'],
  ['junit', testRailOptions],
  ['html', {outputFolder: 'html-report'}],
];

// Define reporters with SlackReporter
const useReportersWithSlack: any[] = [
  [
    './node_modules/playwright-slack-report/dist/src/SlackReporter.js',
    {
      slackOAuthToken: process.env.SLACK_BOT_USER_OAUTH_TOKEN!,
      channels: ['c8-orchestration-cluster-e2e-test-results'],
      sendResults: 'always',
      showInThread: true,
      meta: [
        {
          key: `Nightly Test Results for Mono Repo - ${process.env.VERSION}`,
          value: `<https://github.com/camunda/camunda/actions/runs/${process.env.GITHUB_RUN_ID}|📊>`,
        },
      ],
    },
  ],
  ...useReportersWithoutSlack,
];

const args = process.argv.slice(2);
const changedFoldersArgIndex = args.indexOf('--changed-folders');
const changedFolders =
  changedFoldersArgIndex !== -1
    ? args[changedFoldersArgIndex + 1].split(',')
    : [];

// Default: V2 mode (unless explicitly disabled with CAMUNDA_TASKLIST_V2_MODE_ENABLED=false)
const isV2ModeEnabled =
  process.env.CAMUNDA_TASKLIST_V2_MODE_ENABLED !== 'false';

export default defineConfig({
  testDir: `./tests/`,
  timeout: 12 * 60 * 1000,
  workers: 4,
  retries: 1,
  grep: isV2ModeEnabled ? /^(?!.*@v1-only).*$/ : /^(?!.*@v2-only).*$/,
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL: getBaseURL(),
    actionTimeout: 10000,
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'api-tests',
      testMatch: ['tests/api/**/*.spec.ts'],
      testIgnore: ['tests/api/v2/clock/*.spec.ts'],
      use: devices['Desktop Chrome'],
      teardown: 'clock-api-tests',
    },
    {
      name: 'clock-api-tests',
      testMatch: ['tests/api/v2/clock/*.spec.ts'],
      use: devices['Desktop Chrome'],
      workers: 1,
      fullyParallel: false,
    },
    {
      name: 'chromium',
      use: devices['Desktop Chrome'],
      // Specify only tests in the changed folders for the 'chromium' project
      testMatch: changedFolders.includes('chromium')
        ? changedFolders.map((folder) => `**/${folder}/*.spec.ts`)
        : undefined,
      testIgnore: ['task-panel.spec.ts', 'tests/api/**/*.spec.ts'],
      grep: /^(?!.*@v2-only).*$/,
      teardown: 'chromium-subset',
    },
    {
      name: 'chromium-subset',
      testMatch: 'task-panel.spec.ts',
      grep: /^(?!.*@v1-only).*$/,
      use: devices['Desktop Chrome'],
    },
    {
      name: 'firefox',
      use: devices['Desktop Firefox'],
      testIgnore: ['task-panel.spec.ts', 'tests/api/**/*.spec.ts'],
      grep: /^(?!.*@v2-only).*$/,
      teardown: 'firefox-subset',
    },
    {
      name: 'firefox-subset',
      testMatch: 'task-panel.spec.ts',
      use: devices['Desktop Firefox'],
      grep: /^(?!.*@v2-only).*$/,
    },
    {
      name: 'msedge',
      use: devices['Desktop Edge'],
      testIgnore: ['task-panel.spec.ts', 'tests/api/**/*.spec.ts'],
      grep: /^(?!.*@v2-only).*$/,
      teardown: 'msedge-subset',
    },
    {
      name: 'msedge-subset',
      testMatch: 'task-panel.spec.ts',
      use: devices['Desktop Edge'],
      testIgnore: ['tests/api/**/*.spec.ts'],
      grep: /^(?!.*@v2-only).*$/,
    },
    {
      name: 'tasklist-v1-e2e',
      testMatch: ['tests/tasklist/*.spec.ts', 'tests/tasklist/v1/*.spec.ts'],
      use: devices['Desktop Edge'],
      testIgnore: ['task-panel.spec.ts', 'tests/api/**/*.spec.ts'],
      grep: /^(?!.*@v2-only).*$/,
      teardown: 'chromium-subset',
    },
    {
      name: 'tasklist-v2-e2e',
      testMatch: ['tests/tasklist/*.spec.ts'],
      use: devices['Desktop Edge'],
      testIgnore: [
        'task-panel.spec.ts',
        'tests/tasklist/v1/*.spec.ts',
        'tests/api/**/*.spec.ts',
      ],
      grep: /^(?!.*@v1-only).*$/,
      teardown: 'chromium-subset',
    },
    {
      name: 'identity-e2e',
      testMatch: ['tests/identity/*.spec.ts'],
      testIgnore: ['tests/api/**/*.spec.ts'],
      use: devices['Desktop Chrome'],
    },
    {
      name: 'operate-e2e',
      testMatch: ['tests/operate/*.spec.ts'],
      use: devices['Desktop Chrome'],
      testIgnore: ['tests/api/**/*.spec.ts'],
    },
  ],
  reporter:
    process.env.INCLUDE_SLACK_REPORTER === 'true'
      ? useReportersWithSlack
      : useReportersWithoutSlack,
});

function getBaseURL(): string {
  if (typeof process.env.PLAYWRIGHT_BASE_URL === 'string') {
    return process.env.PLAYWRIGHT_BASE_URL;
  }
  return 'http://localhost:8080';
}
