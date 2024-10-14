/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {fetchUrl, matchRegex} from './services';
import {Branch} from './types';

export class GitHubService {
  constructor(
    private readonly githubToken: string,
    private readonly org: string,
    private readonly repo: string,
  ) {}

  public getBranchesWithPrefix = async (refPrefix: string): Promise<string[]> => {
    const releaseBranchesREfs = await this.fetchRefs(refPrefix.split('/')[0]);
    const releaseBranches = releaseBranchesREfs
      .map((branch) =>
        matchRegex(branch.ref, new RegExp(`refs/heads/(${refPrefix}\\d+.\\d+)`)),
      )
      .filter((name): name is string => name !== undefined);
    return releaseBranches;
  };

  private fetchRefs = async (refPrefix: string) => {
    const refs = await fetchUrl<Branch[]>(
      `https://api.github.com/repos/${this.org}/${this.repo}/git/refs/heads/${refPrefix}`,
      `token ${this.githubToken}`,
    );

    if (!Array.isArray(refs)) {
      return [];
    }

    return refs;
  };

  public sortBranches = (firstBranch: string, secondBranch: string) => {
    const [firstBranchName, firstMain, firstMinor] = this.getBranchVersion(firstBranch);
    const [secondBranchName, secondMain, secondMinor] = this.getBranchVersion(secondBranch);

    if (!(firstMain && firstMinor && secondMain && secondMinor)) {
      return 0;
    }

    if (firstBranchName !== secondBranchName) {
      return secondBranchName > firstBranchName ? 1 : -1;
    }

    if (firstMain !== secondMain) {
      return secondMain - firstMain;
    } else {
      return secondMinor - firstMinor;
    }
  };

  private getBranchVersion = (branch: string): [string, ...number[]] | [string, undefined] => {
    const [branchName, version] = branch.split('/');

    if (!version) {
      return [branchName, undefined];
    }

    return [branchName, ...version.split('.').map(Number)];
  };
}
