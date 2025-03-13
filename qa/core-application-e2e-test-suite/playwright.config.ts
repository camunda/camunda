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
      slackWebHookUrl: process.env.SLACK_WEBHOOK_URL!,
      sendResults: 'always',
      meta: [
        {
          key: (() => {
            switch (process.env.MINOR_VERSION) {
              case '8.8':
                return `Test Results for mono repo - 8.8`;
              case '8.7':
                return `Test Results for mono repo - 8.7`;
              case '8.6':
                return 'Test Results for mono repo - 8.6';
              default:
                return 'Test Results';
            }
          })(),
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
  testDir: process.env.MINOR_VERSION
    ? `./tests/${process.env.MINOR_VERSION}/`
    : `./tests/`,
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
      dependencies: ['chromium-setup'],
      // Specify only tests in the changed folders for the 'chromium' project
      testMatch: changedFolders.includes('chromium')
        ? changedFolders.map((folder) => `**/${folder}/*.spec.ts`)
        : undefined,
    },
    {
      name: 'firefox',
      use: devices['Desktop Firefox'],
      dependencies: ['firefox-setup'],
    },
    {
      name: 'msedge',
      use: devices['Desktop Edge'],
      dependencies: ['edge-setup'],
    },
    {
      name: 'chromium-setup',
      use: devices['Desktop Chrome'],
    },
    {
      name: 'firefox-setup',
      use: devices['Desktop Firefox'],
    },
    {
      name: 'edge-setup',
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
