/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import path from 'path';

import { createJsonFile, isRegExp, readJsonFIle } from './services';
import { Config } from './types';
import { GitHubService } from './GitHubService';

const ARGOCD_PROJECTS = ['optimize-previews'];
const ARGOCD_URL = 'https://argocd.int.camunda.com';

const GITHUB_TOKEN = process.env.GITHUB_TOKEN;
const GITHUB_ORG = 'camunda';
const GITHUB_REPO = 'camunda-optimize';

const RENOVATE_CONFIG_PATH = path.join(process.cwd(), '../../renovate.json');

const CONFIG_FILE_NAME = 'config.json';

async function createHealthStatusConfig() {
  const githubService = new GitHubService(GITHUB_TOKEN, GITHUB_ORG, GITHUB_REPO);

  const renovateStringBranches = getRenovateStringBranches();
  const releaseBranches = await githubService.getBranchesWithPrefix('release');
  const ciBranches = [...releaseBranches, ...renovateStringBranches].sort(
    githubService.sortBranches,
  );
  const maintenanceBranches = ciBranches.filter((branch) => branch.includes('maintenance'));

  const config: Partial<Config> = {
    argoCd: {
      projects: ARGOCD_PROJECTS,
      url: ARGOCD_URL,
    },
    github: {
      organization: GITHUB_ORG,
      repository: GITHUB_REPO,
      defaultBranch: 'master',
      workflows: [
        {
          name: 'optimize-ci',
          branches: ciBranches,
        },
        {
          name: 'ci',
          branches: maintenanceBranches,
        },
        'optimize-zeebe-compatibility',
        'optimize-java-compatibility',
        'optimize-engine-compatibility',
        'optimize-es-compatibility',
        'optimize-os-compatibility',
        'optimize-upgrade-data-performance',
        'optimize-import-dynamic-data-performance',
        'optimize-import-static-data-performance',
        'optimize-import-mediator-permutation',
        'optimize-history-cleanup-performance',
        'optimize-connect-to-secured-es',
        'optimize-cluster-test',
        'optimize-e2e-tests',
        'optimize-release-optimize',
        'optimize-trivy-check',
      ],
    },
    githubPrs: {
      repository: GITHUB_REPO,
      organization: GITHUB_ORG,
      prs: [
        {
          author: 'renovate[bot]',
          labels: ['dependencies', 'component:frontend'],
          resultType: 'count',
          title: 'Open renovate FE PRs',
          state: 'open',
        },
        {
          author: 'renovate[bot]',
          labels: ['dependencies', 'component:backend'],
          resultType: 'count',
          title: 'Open renovate BE PRs',
          state: 'open',
        },
        {
          author: 'renovate[bot]',
          labels: ['dependencies', 'component:ci'],
          resultType: 'count',
          title: 'Open renovate CI PRs',
          state: 'open',
        },
      ],
    },
  };

  return createJsonFile(CONFIG_FILE_NAME, config);
}

function getRenovateStringBranches() {
  const renovateConfig = readJsonFIle<{ baseBranches: string[] }>(RENOVATE_CONFIG_PATH);
  return renovateConfig.baseBranches.filter((branch) => !isRegExp(branch));
}

createHealthStatusConfig();
