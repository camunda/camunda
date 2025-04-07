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
      channels: ['core-application-e2e-test-results'],
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
  use: {
    baseURL: getBaseURL(),
    actionTimeout: 10000,
  },
  projects: [
    {
      name: 'chromium',
      use: devices['Desktop Chrome'],
      // Specify only tests in the changed folders for the 'chromium' project
      testMatch: changedFolders.includes('chromium')
        ? changedFolders.map((folder) => `**/${folder}/*.spec.ts`)
        : undefined,
    },
    {
      name: 'firefox',
      use: devices['Desktop Firefox'],
    },
    {
      name: 'msedge',
      use: devices['Desktop Edge'],
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
