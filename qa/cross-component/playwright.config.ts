/* eslint-disable */
import {devices, defineConfig} from '@playwright/test';
import * as dotenv from 'dotenv';

dotenv.config();
const projectName = process.env.PROJECT! || 'Chromium'; // Default to 'default-project' if PROJECT is not set

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
              case 'c8Run-8.7':
                return `Test Results for c8Run with Latest 8.7 version - OS: ${process.env.OS}`;
              case 'SM-8.7':
                return `Test Results for Self-Managed with Helm with Latest SNAPSHOT Versions - ${projectName}`;
              case '8.3':
                return 'Test Results for SaaS with Latest 8.3 Patch version';
              case '8.4':
                return 'Test Results for SaaS with Latest 8.4 Patch version';
              case '8.5':
                return 'Test Results for SaaS with Latest 8.5 Patch version';
              case '8.6':
                return 'Test Results for SaaS with Latest 8.6 Patch version';
              case '8.7':
                return process.env.RELEASE_VERSION === 'true'
                  ? 'Test Results for SaaS with a Cluster with latest 8.7 versions of all components'
                  : 'Test Results for SaaS with a Cluster with SNAPSHOT versions of all components';
              default:
                return 'Test Results';
            }
          })(),
          value: `<https://github.com/camunda/c8-cross-component-e2e-tests/actions/runs/${process.env.GITHUB_RUN_ID}|📊>`,
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
  workers: process.env.MINOR_VERSION?.includes('SM') ? 7 : 9,
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
        testIgnore: process.env.MINOR_VERSION?.includes('SM')
        ? 'permissions-and-access-tests.spec.ts'
        : 'cluster-setup.spec.ts'
    },
    {
      name: 'firefox',
      use: devices['Desktop Firefox'],
      dependencies: ['firefox-setup'],
      testIgnore: process.env.MINOR_VERSION?.includes('SM')
        ? 'permissions-and-access-tests.spec.ts'
        : 'cluster-setup.spec.ts'
    },
    {
      name: 'msedge',
      use: devices['Desktop Edge'],
      dependencies: ['edge-setup'],
      testIgnore: process.env.MINOR_VERSION?.includes('SM')
        ? 'permissions-and-access-tests.spec.ts'
        : 'cluster-setup.spec.ts'
    },
    {
      name: 'chromium-setup',
      testMatch: process.env.MINOR_VERSION?.includes('SM')
      ? 'permissions-and-access-tests.spec.ts'
      : 'cluster-setup.spec.ts',
      use: devices['Desktop Chrome'],
    },
    {
      name: 'firefox-setup',
      testMatch: process.env.MINOR_VERSION?.includes('SM')
        ? 'permissions-and-access-tests.spec.ts'
        : 'cluster-setup.spec.ts',
      use: devices['Desktop Firefox'],
    },
    {
      name: 'edge-setup',
      testMatch: process.env.MINOR_VERSION?.includes('SM')
        ? 'permissions-and-access-tests.spec.ts'
        : 'cluster-setup.spec.ts',
      use: devices['Desktop Edge'],
    },
  ],
  reporter:
    process.env.INCLUDE_SLACK_REPORTER === 'true'
      ? useReportersWithSlack
      : useReportersWithoutSlack,
});

function getBaseURL(): string {
  if (process.env.IS_PROD === 'true') {
    return 'https://console.camunda.io';
  }
  
  if (typeof process.env.PLAYWRIGHT_BASE_URL === 'string') {
    return process.env.PLAYWRIGHT_BASE_URL;
  }
  
  if (process.env.MINOR_VERSION?.includes('SM')) {
    return 'http://gke-' + process.env.BASE_URL + '.ci.distro.ultrawombat.com';
  }
  
  if (process.env.MINOR_VERSION?.includes('Run')) {
    return 'http://localhost:8080';
  }
  
  return 'https://console.cloud.ultrawombat.com';
}
