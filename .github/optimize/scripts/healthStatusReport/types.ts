/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

export type VulnLevel = 'critical' | 'high' | 'medium' | 'low';

export type GithubWorkflow =
  | {
      name: string;
      branches?: string[];
    }
  | string;

export type PRParams = {
  author?: string;
  base?: string;
  labels?: string[];
  state?: PRState;
  title?: string;
  resultType?: PRResultType;
};
export type PRState = 'open' | 'close' | 'all';

export type PRResultType = 'list' | 'count';

export type Config = {
  github: {
    title?: string;
    defaultBranch?: string;
    organization: string;
    repository: string;
    workflows: GithubWorkflow[];
  };
  githubPrs: {
    title?: string;
    organization: string;
    repository: string;
    prs: PRParams[];
  };
};

export type Branch = {
  ref: string;
};

export type SnykProject = {
  attributes: {
    name: string;
  };
};

export type SnykProjectsResponse = {
  data: SnykProject[];
};
