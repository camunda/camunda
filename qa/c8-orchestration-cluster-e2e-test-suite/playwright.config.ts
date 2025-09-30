/* eslint-disable */
import { devices, defineConfig } from '@playwright/test';
import * as dotenv from 'dotenv';

dotenv.config();

const testRailOptions = {
  embedAnnotationsAsProperties: true,
  outputFile: './test-results/junit-report.xml',
};

const isV2StatelessTestsOnly = process.env.V2_STATELESS_TESTS === 'true';

// Default: V2 mode (unless CAMUNDA_TASKLIST_V1_MODE_ENABLED=true)
const isV2ModeEnabled = process.env.CAMUNDA_TASKLIST_V1_MODE_ENABLED !== 'true';

// Define reporters without SlackReporter
const useReportersWithoutSlack: any[] = [
  ['list'],
  ['junit', testRailOptions],
  ['html', { outputFolder: 'html-report' }],
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
          key: isV2StatelessTestsOnly
            ? `Nightly V2 Stateless Test Results for Mono Repo - ${process.env.VERSION}`
            : `Nightly Test Results for Mono Repo - ${process.env.VERSION}`,
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

// Define normal projects (for QA orchestration cluster)
const normalProjects = [
  {
    name: 'chromium',
    use: devices['Desktop Chrome'],
    testMatch: changedFolders.includes('chromium')
      ? changedFolders.map((folder) => `**/${folder}/*.spec.ts`)
      : ['tests/**/*.spec.ts'],
    testIgnore: ['tests/tasklist/task-panel.spec.ts', 'v2-stateless-tests/**'],
    teardown: 'chromium-subset',
  },
  {
    name: 'chromium-subset',
    testMatch: 'tests/tasklist/task-panel.spec.ts',
    grep: /^(?!.*@v1-only).*$/,
    use: devices['Desktop Chrome'],
    testIgnore: 'v2-stateless-tests/**',
  },
  {
    name: 'firefox',
    use: devices['Desktop Firefox'],
    testIgnore: ['tests/tasklist/task-panel.spec.ts', 'v2-stateless-tests/**'],
    teardown: 'firefox-subset',
  },
  {
    name: 'firefox-subset',
    testMatch: 'tests/tasklist/task-panel.spec.ts',
    use: devices['Desktop Firefox'],
    testIgnore: 'v2-stateless-tests/**',
  },
  {
    name: 'msedge',
    use: devices['Desktop Edge'],
    testIgnore: ['tests/tasklist/task-panel.spec.ts', 'v2-stateless-tests/**'],
    teardown: 'msedge-subset',
  },
  {
    name: 'msedge-subset',
    testMatch: 'tests/tasklist/task-panel.spec.ts',
    use: devices['Desktop Edge'],
    testIgnore: 'v2-stateless-tests/**',
  },
  {
    name: 'tasklist-v1-e2e',
    testMatch: ['tests/tasklist/*.spec.ts', 'tests/tasklist/v1/*.spec.ts'],
    use: devices['Desktop Edge'],
    testIgnore: ['tests/tasklist/task-panel.spec.ts', 'v2-stateless-tests/**'],
    grep: /@v1-only/, // Explicitly run only v1 tests
    teardown: 'chromium-subset',
  },
  {
    name: 'tasklist-v2-e2e',
    testMatch: ['tests/tasklist/*.spec.ts'],
    use: devices['Desktop Edge'],
    testIgnore: [
      'tests/tasklist/task-panel.spec.ts',
      'tests/tasklist/v1/*.spec.ts',
      'v2-stateless-tests/**',
    ],
    grep: /^(?!.*@v1-only).*$/, // Exclude v1-only
    teardown: 'chromium-subset',
  },
  {
    name: 'identity-e2e',
    testMatch: ['tests/identity/*.spec.ts'],
    use: devices['Desktop Chrome'],
    testIgnore: 'v2-stateless-tests/**',
  },
];

const v2StatelessProjects = [
  {
    name: 'request-validation-tests',
    testMatch: ['v2-stateless-tests/tests/request-validation/*.spec.ts'],
    use: devices['Desktop Chrome'],
  },
];

export default defineConfig({
  testDir: `./`,
  timeout: 12 * 60 * 1000,
  workers: 4,
  retries: 1,
  // Exclude @v1-only tests when in V2 mode (default)
  grep: isV2ModeEnabled ? /^(?!.*@v1-only).*$/ : undefined,
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL: getBaseURL(),
    actionTimeout: 10000,
    screenshot: 'only-on-failure',
  },
  projects: isV2StatelessTestsOnly ? v2StatelessProjects : normalProjects,
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