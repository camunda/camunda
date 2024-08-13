/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createJsonFile} from './services';
import {Config} from './types';
import {GitHubService} from './GitHubService';

const ARGOCD_PROJECTS = ['optimize-previews'];
const ARGOCD_URL = 'https://argocd.int.camunda.com';

const GITHUB_TOKEN = process.env.GITHUB_TOKEN;
const GITHUB_ORG = 'camunda';
const GITHUB_REPO = 'camunda';

const CONFIG_FILE_NAME = 'config.json';

async function createHealthStatusConfig() {
  const githubService = new GitHubService(GITHUB_TOKEN, GITHUB_ORG, GITHUB_REPO);
  const defaultBranch = 'main';
  const releaseBranches = await githubService.getBranchesWithPrefix('release/optimize-');
  const stableBranches = await githubService.getBranchesWithPrefix('stable');
  const ciBranches = [defaultBranch, ...releaseBranches, ...stableBranches].sort(
    githubService.sortBranches,
  );

  const config: Partial<Config> = {
    argoCd: {
      projects: ARGOCD_PROJECTS,
      url: ARGOCD_URL,
    },
    github: {
      organization: GITHUB_ORG,
      repository: GITHUB_REPO,
      defaultBranch,
      workflows: [
        {
          name: 'optimize-ci',
          branches: ciBranches,
        },
        'optimize-zeebe-compatibility',
        'optimize-es-compatibility',
        'optimize-os-compatibility',
        'optimize-e2e-tests-sm',
        'optimize-e2e-test-cloud',
        'optimize-release-optimize-c8-only',
      ],
    },
  };

  return createJsonFile(CONFIG_FILE_NAME, config);
}

createHealthStatusConfig();
