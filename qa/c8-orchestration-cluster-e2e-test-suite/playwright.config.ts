/* eslint-disable */
import {devices, defineConfig} from '@playwright/test';
import * as dotenv from 'dotenv';

dotenv.config();

const testRailOptions = {
  embedAnnotationsAsProperties: true,
  outputFile: './test-results/junit-report.xml',
};

const isV2StatelessTestsOnly = process.env.V2_STATELESS_TESTS === 'true';
const isApiTestsOnly = process.env.API_TESTS_ONLY === 'true';

// Determine the test type for Slack reporting
function getTestTypeLabel(): string {
  if (isV2StatelessTestsOnly) {
    return `Nightly V2 Stateless Test Results for Mono Repo - ${process.env.VERSION}`;
  }
  if (isApiTestsOnly) {
    const database = process.env.DATABASE
      ? ` - Database ${process.env.DATABASE}${
          process.env.DB_NAME ? ` (${process.env.DB_NAME})` : ''
        }`
      : '';
    return `Nightly API Test Results for Mono Repo - ${process.env.VERSION}${database}`;
  }
  return `Nightly Test Results for Mono Repo - ${process.env.VERSION}`;
}

// Reporters
const useReportersWithoutSlack: any[] = [
  ['list'],
  ['junit', testRailOptions],
  ['html', {outputFolder: 'html-report'}],
  ['json', {outputFile: `json-report/results.json`}],
];

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
          key: getTestTypeLabel(),
          value: `<https://github.com/camunda/camunda/actions/runs/${process.env.GITHUB_RUN_ID}|📊>`,
        },
      ],
    },
  ],
  ...useReportersWithoutSlack,
];

// Detect changed folders
const args = process.argv.slice(2);
const changedFoldersArgIndex = args.indexOf('--changed-folders');
const changedFolders =
  changedFoldersArgIndex !== -1
    ? args[changedFoldersArgIndex + 1].split(',')
    : [];

const apiTestMatch = ['tests/api/v2/**/*.spec.ts'];

const apiTestIgnore = [
  'tests/api/v2/clock/*.spec.ts',
  'tests/api/v2/usage-metrics/*.spec.ts',
  'tests/api/v2/audit-log/*.spec.ts',
  'tests/api/v2/job/job-statistics-*.spec.ts',
];
// Projects
const normalProjects = [
  {
    name: 'api-tests',
    testMatch: apiTestMatch,
    testIgnore: apiTestIgnore,
    use: devices['Desktop Chrome'],
    teardown: 'api-tests-subset',
  },
  {
    name: 'api-tests-subset',
    testMatch: [
      'tests/api/v2/clock/*.spec.ts',
      'tests/api/v2/usage-metrics/*.spec.ts',
      'tests/api/v2/audit-log/*.spec.ts',
      'tests/api/v2/job/job-statistics-*.spec.ts',
    ],
    use: devices['Desktop Chrome'],
    workers: 1,
    fullyParallel: false,
  },
  {
    name: 'chromium',
    use: devices['Desktop Chrome'],
    testMatch: changedFolders.includes('chromium')
      ? changedFolders.map((folder) => `**/${folder}/*.spec.ts`)
      : ['tests/**/*.spec.ts'],
    testIgnore: [
      'tests/tasklist/task-panel.spec.ts',
      'v2-stateless-tests/**',
      'tests/api/**/*.spec.ts',
    ],
    teardown: 'chromium-subset',
  },
  {
    name: 'chromium-subset',
    testMatch: 'tests/tasklist/task-panel.spec.ts',
    use: devices['Desktop Chrome'],
    testIgnore: ['v2-stateless-tests/**', 'tests/api/**/*.spec.ts'],
  },
  {
    name: 'firefox',
    use: devices['Desktop Firefox'],
    testMatch: changedFolders.includes('firefox')
      ? changedFolders.map((folder) => `**/${folder}/*.spec.ts`)
      : ['tests/**/*.spec.ts'],
    testIgnore: [
      'tests/tasklist/task-panel.spec.ts',
      'v2-stateless-tests/**',
      'tests/api/**/*.spec.ts',
    ],
    teardown: 'firefox-subset',
  },
  {
    name: 'firefox-subset',
    testMatch: 'tests/tasklist/task-panel.spec.ts',
    use: devices['Desktop Firefox'],
    testIgnore: ['v2-stateless-tests/**', 'tests/api/**/*.spec.ts'],
  },
  {
    name: 'msedge',
    use: devices['Desktop Edge'],
    testMatch: changedFolders.includes('msedge')
      ? changedFolders.map((folder) => `**/${folder}/*.spec.ts`)
      : ['tests/**/*.spec.ts'],
    testIgnore: [
      'tests/tasklist/task-panel.spec.ts',
      'v2-stateless-tests/**',
      'tests/api/**/*.spec.ts',
    ],
    teardown: 'msedge-subset',
  },
  {
    name: 'msedge-subset',
    testMatch: 'tests/tasklist/task-panel.spec.ts',
    use: devices['Desktop Edge'],
    testIgnore: ['v2-stateless-tests/**', 'tests/api/**/*.spec.ts'],
  },
  {
    name: 'tasklist-e2e',
    testMatch: ['tests/tasklist/*.spec.ts'],
    use: devices['Desktop Edge'],
    testIgnore: [
      'tests/tasklist/task-panel.spec.ts',
      'v2-stateless-tests/**',
      'tests/api/**/*.spec.ts',
    ],
    teardown: 'chromium-subset',
  },
  {
    name: 'identity-e2e',
    testMatch: ['tests/identity/*.spec.ts'],
    use: devices['Desktop Chrome'],
    testIgnore: ['v2-stateless-tests/**', 'tests/api/**/*.spec.ts'],
  },
  {
    name: 'operate-e2e',
    testMatch: ['tests/operate/*.spec.ts'],
    use: devices['Desktop Chrome'],
    testIgnore: ['v2-stateless-tests/**', 'tests/api/**/*.spec.ts'],
  },
  // ── Optimize variable-export scope tests ──────────────────────────────────
  // Each project targets a specific Optimize server configuration.
  // Point OPTIMIZE_BASE_URL at an Optimize instance started with the matching
  // service-config.yaml settings before running a project.
  //
  // Run a single project:
  //   npx playwright test --project=optimize-default-config
  //   npx playwright test --project=optimize-local-vars-disabled
  //   npx playwright test --project=optimize-root-vars-disabled
  //   npx playwright test --project=optimize-import-disabled
  {
    // TC-01, TC-02, TC-20, TC-21
    // Optimize defaults: exportRootVariables=true, exportLocalVariables=true
    name: 'optimize-default-config',
    testMatch: ['tests/api/v2/optimize/**/*.spec.ts'],
    use: {
      ...devices['Desktop Chrome'],
      // Both scope flags default to true — no special Optimize config required
    },
  },
  {
    // TC-04 to TC-16 (local variables disabled, optional whitelist)
    // Requires Optimize started with:
    //   zeebe.exportLocalVariables: false
    //   zeebe.localVariableNameFilters: <comma-separated patterns>
    name: 'optimize-local-vars-disabled',
    testMatch: ['tests/api/v2/optimize/**/*.spec.ts'],
    use: {
      ...devices['Desktop Chrome'],
    },
    // Signal the test file that local export is disabled
    grep: /local.*disabled|whitelist|pattern matching|whitelist.*updates/i,
  },
  {
    // TC-07: root variables disabled, local enabled
    // Requires Optimize started with:
    //   zeebe.exportRootVariables: false
    //   zeebe.exportLocalVariables: true
    name: 'optimize-root-vars-disabled',
    testMatch: ['tests/api/v2/optimize/**/*.spec.ts'],
    grep: /root.*disabled|root-scope export while keeping local/i,
  },
  {
    // TC-03, TC-08: variableImportEnabled=false or both flags = false
    // Requires Optimize started with:
    //   zeebe.variableImportEnabled: false  OR
    //   zeebe.exportRootVariables: false + zeebe.exportLocalVariables: false
    name: 'optimize-import-disabled',
    testMatch: ['tests/api/v2/optimize/**/*.spec.ts'],
    grep: /variableImportEnabled.*false|both.*disabled|no variable data/i,
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
  expect: {
    timeout: 10_000,
  },
  use: {
    baseURL: getBaseURL(),
    actionTimeout: 10000,
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
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
