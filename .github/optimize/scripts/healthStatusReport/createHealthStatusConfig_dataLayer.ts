/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createJsonFile} from './services';
import {Config} from './types';
import {GitHubService} from './GitHubService';

const GITHUB_TOKEN = process.env.GITHUB_TOKEN;
const GITHUB_ORG = 'camunda';
const GITHUB_REPO = 'camunda';
const MAIN_BRANCH = 'main';

const CONFIG_FILE_NAME = 'config_dl.json';

async function createHealthStatusConfig_dataLayer() {
  const githubService = new GitHubService(GITHUB_TOKEN, GITHUB_ORG, GITHUB_REPO);
  const releaseBranches = await githubService.getBranchesWithPrefix('release/optimize-');
  const optimizeStableBranches = await githubService.getBranchesWithPrefix('stable/optimize-');
  const ciBranches = [MAIN_BRANCH, ...releaseBranches, ...optimizeStableBranches].sort(
    githubService.sortBranches,
  );

  const config: Partial<Config> = {
    github: {
      organization: GITHUB_ORG,
      repository: GITHUB_REPO,
      defaultBranch: MAIN_BRANCH,
      workflows: [
        {
          name: 'optimize-ci-data-layer',
          branches: ciBranches,
        },
      ],
    },
  };

  return createJsonFile(CONFIG_FILE_NAME, config);
}

createHealthStatusConfig_dataLayer();
