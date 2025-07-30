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

export default defineConfig({
  testDir: `./tests/`,
  timeout: 12 * 60 * 1000,
  workers: 4,
  retries: 1,
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
      name: 'chromium',
      use: devices['Desktop Chrome'],
      // Specify only tests in the changed folders for the 'chromium' project
      testMatch: changedFolders.includes('chromium')
        ? changedFolders.map((folder) => `**/${folder}/*.spec.ts`)
        : undefined,
      testIgnore: 'task-panel.spec.ts',
      teardown: 'chromium-subset',
    },
    {
      name: 'chromium-subset',
      testMatch: 'task-panel.spec.ts',
      use: devices['Desktop Chrome'],
    },
    {
      name: 'firefox',
      use: devices['Desktop Firefox'],
      testIgnore: 'task-panel.spec.ts',
      teardown: 'firefox-subset',
    },
    {
      name: 'firefox-subset',
      testMatch: 'task-panel.spec.ts',
      use: devices['Desktop Firefox'],
    },
    {
      name: 'msedge',
      use: devices['Desktop Edge'],
      testIgnore: 'task-panel.spec.ts',
      teardown: 'msedge-subset',
    },
    {
      name: 'msedge-subset',
      testMatch: 'task-panel.spec.ts',
      use: devices['Desktop Edge'],
    },
    {
      name: 'tasklist-v1-e2e',
      testMatch: ['tests/tasklist/*.spec.ts', 'tests/tasklist/v1/*.spec.ts'],
      use: devices['Desktop Edge'],
      testIgnore: 'task-panel.spec.ts',
      teardown: 'chromium-subset',
    },
    {
      name: 'identity-e2e',
      testMatch: ['tests/identity/*.spec.ts'],
      use: devices['Desktop Chrome'],
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
