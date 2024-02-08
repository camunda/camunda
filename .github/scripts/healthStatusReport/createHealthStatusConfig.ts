/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fetchUrl from './fetchUrl';
import fs from 'fs';

type VulnLevel = 'critical' | 'high' | 'medium' | 'low';

type GithubWorkflow =
  | {
      name: string;
      branches?: string[];
    }
  | string;

type PRParams = {
  author?: string;
  base?: string;
  labels?: string[];
  state?: PRState;
  title?: string;
  resultType?: PRResultType;
};
type PRState = 'open' | 'close' | 'all';

type PRResultType = 'list' | 'count';

type Config = {
  github: {
    title?: string;
    defaultBranch?: string;
    organization: string;
    repository: string;
    workflows: GithubWorkflow[];
  };
  argoCd: {
    title?: string;
    url: string;
    projects: string[];
  };
  snyk: {
    title?: string;
    organization: string;
    vulnLevels?: VulnLevel[];
    projects: {
      project: string;
      origin: string;
      versions: string[];
    }[];
  };
  githubPrs: {
    title?: string;
    organization: string;
    repository: string;
    prs: PRParams[];
  };
};

type Branch = {
  ref: string;
};

type SnykProject = {
  name: string;
  issueCountsBySeverity: {
    low: number;
    medium: number;
    high: number;
    critical: number;
  };
};

type SnykProjectsResponse = {
  projects: SnykProject[];
};

async function createHealthStatusConfig() {
  const ciBranches = (await getCiBranches()).filter((branch) => !branch.includes('3.8'));
  const snykProjects = (await fetchSnykProjects()).filter(
    (project) => !project.name.includes('3.8'),
  );

  const config: Partial<Config> = {
    argoCd: {
      projects: ['optimize-previews'],
      url: 'https://argocd.int.camunda.com',
    },
    github: {
      organization: 'camunda',
      repository: 'camunda-optimize',
      defaultBranch: 'master',
      workflows: [
        {
          name: 'ci',
          branches: ciBranches,
        },
        'connect-to-secured-es',
        'zeebe-compatibility',
        'upgrade-data-performance',
        'cluster-test',
        'java-compatibility',
        'import-dynamic-data-performance',
        'e2e-tests',
        'release-optimize',
        'engine-compatibility',
      ],
    },
    snyk: {
      organization: 'team-optimize',
      vulnLevels: ['critical', 'high', 'medium', 'low'],
      projects: [
        {
          project: 'camunda/camunda-optimize',
          versions: ['master'],
          origin: 'github',
        },
        {
          project: 'camunda/optimize',
          versions: getHighestDockerVersions(snykProjects),
          origin: 'docker-hub',
        },
      ],
    },
    githubPrs: {
      repository: 'camunda-optimize',
      organization: 'camunda',
      prs: [
        {
          author: 'renovate[bot]',
          labels: ['renovate', 'component:frontend'],
          resultType: 'count',
          title: 'Open renovate FE PRs',
          state: 'open',
        },
        {
          author: 'renovate[bot]',
          labels: ['renovate', 'component:backend'],
          resultType: 'count',
          title: 'Open renovate BE PRs',
          state: 'open',
        },
        {
          author: 'renovate[bot]',
          labels: ['renovate', 'component:infra'],
          resultType: 'count',
          title: 'Open renovate Infra PRs',
          state: 'open',
        },
      ],
    },
  };

  return fs.writeFileSync('config.json', JSON.stringify(config), 'utf-8');
}

async function getCiBranches() {
  const maintenanceBranches = new Set<string>();
  (await fetchCiBranches())
    .map(branchToBranchName)
    .filter((name): name is string => name !== undefined)
    .sort(sortCiBranches)
    .forEach((name) => maintenanceBranches.add(name));
  return ['master', ...maintenanceBranches];
}

async function fetchCiBranches() {
  return fetchUrl<Branch[]>(
    'https://api.github.com/repos/camunda/camunda-optimize/git/refs/heads/maintenance',
    `token ${process.env.GITHUB_TOKEN}`,
  );
}
function branchToBranchName(branch: Branch): string | undefined {
  const regex = /refs\/heads\/(maintenance\/\d+\.\d+)/;
  const match = branch.ref.match(regex);

  return match?.[1];
}

function sortCiBranches(firstBranch: string, secondBranch: string) {
  const [firstMain, firstMinor] = getBranchVersion(firstBranch);
  const [secondMain, secondMinor] = getBranchVersion(secondBranch);

  if (firstMain !== secondMain) {
    return secondMain - firstMain;
  } else {
    return secondMinor - firstMinor;
  }
}

function getBranchVersion(branch: string) {
  return branch.split('/')[1].split('.').map(Number);
}

async function fetchSnykProjects() {
  return (
    (
      await fetchUrl<SnykProjectsResponse>(
        'https://snyk.io/api/v1/org/team-optimize/projects',
        `token ${process.env.SNYK_TOKEN}`,
      )
    ).projects || []
  );
}

function getHighestDockerVersions(projects: SnykProject[]) {
  const camundaOptimizeProjects = projects.filter((project) =>
    project.name.includes('camunda/optimize:'),
  );
  const uniqueVersions = getUniqueProjectVersions(camundaOptimizeProjects).sort(sortSnykBranches);
  return getHighestProjectVersions(uniqueVersions);
}

function sortSnykBranches(firstBranch: string, secondBranch: string) {
  const [firstMain, firstMinor] = firstBranch.split('.').map(Number);
  const [secondMain, secondMinor] = secondBranch.split('.').map(Number);

  if (firstBranch && !firstMain) {
    return -1;
  } else if (secondBranch && !secondMain) {
    return 1;
  } else if (firstMain !== secondMain) {
    return secondMain - firstMain;
  } else {
    return secondMinor - firstMinor;
  }
}

function getUniqueProjectVersions(projects: SnykProject[]) {
  const projectVersions = new Set<string>();
  projects.forEach((project) => projectVersions.add(project.name.split(':')[1]));
  return [...projectVersions];
}

function getHighestProjectVersions(projectVersions: string[]) {
  const minorVersionsMap = new Map<string, number | string>();

  // Group versions by minor version
  for (const version of projectVersions) {
    const [major, minor, patch = version] = version.split('.');
    const minorVersionKey = major && minor ? `${major}.${minor}` : version;
    let formatedPatch: number | string = +patch;
    if (patch.includes('-')) {
      continue;
    } else if (Number.isNaN(formatedPatch)) {
      formatedPatch = version;
    }
    if (
      !minorVersionsMap.has(minorVersionKey) ||
      (typeof formatedPatch === 'number' &&
        formatedPatch > (+minorVersionsMap.get(minorVersionKey) || 0))
    ) {
      minorVersionsMap.set(minorVersionKey, formatedPatch);
    }
  }

  // Generate the result array
  const result: string[] = [];
  for (const [minorVersionKey, patch] of minorVersionsMap.entries()) {
    if (minorVersionKey === patch) {
      result.push(minorVersionKey);
    } else {
      result.push(`${minorVersionKey}.${patch}`);
    }
  }

  return result;
}

createHealthStatusConfig();
