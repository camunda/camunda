/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fetchUrl from './fetchUrl';

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

export default async function getSnykStatus() {
  const projects = await fetchSnykProjects();

  const snykProjects = [
    {
      prefix: 'camunda/camunda-optimize',
      versions: ['master'],
      origin: 'github',
    },
    {
      prefix: 'camunda/optimize',
      versions: getHighestDockerVersions(projects),
      origin: 'docker-hub',
    },
  ];

  const projectsSummary = snykProjects.map(({origin, prefix, versions}) =>
    versions.map((version) =>
      getProjectSummary(
        projects,
        version,
        getProjectIdentifier(prefix, version),
        getProjectUrl(prefix, version, origin),
      ),
    ),
  );

  return projectsSummary.flat().map(formatResults).join('\n');
}

function getHighestDockerVersions(projects: SnykProject[]) {
  const camundaOptimizeProjects = projects.filter((project) =>
    project.name.includes('camunda/optimize:'),
  );
  const uniqueVersions = getUniqueProjectVersions(camundaOptimizeProjects).sort(sortBranches);
  return getHighestProjectVersions(uniqueVersions);
}

function getUniqueProjectVersions(projects: SnykProject[]) {
  const projectVersions = new Set<string>();
  projects.forEach((project) => projectVersions.add(project.name.split(':')[1]));
  return [...projectVersions];
}

function getHighestProjectVersions(projectVersions: string[]) {
  const minorVersionsMap = new Map();

  // Group versions by minor version
  for (const version of projectVersions) {
    const [major, minor, patch = version] = version.split('.').map(Number);
    const minorVersionKey = major && minor ? `${major}.${minor}` : version;

    if (!minorVersionsMap.has(minorVersionKey) || patch > minorVersionsMap.get(minorVersionKey)) {
      minorVersionsMap.set(minorVersionKey, patch);
    }
  }

  // Generate the result array
  const result = [];
  for (const [minorVersionKey, patch] of minorVersionsMap.entries()) {
    if (minorVersionKey === patch) {
      result.push(minorVersionKey);
    } else {
      result.push(`${minorVersionKey}.${patch}`);
    }
  }

  return result;
}

function sortBranches(firstBranch: string, secondBranch: string) {
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

function getProjectSummary(
  projects: SnykProject[],
  projectName: string,
  projectIdentifier: string,
  projectUrl: string,
) {
  return projects
    .filter((project) => project.name.includes(projectIdentifier))
    .reduce(
      (acc, curr) => {
        acc.high += curr.issueCountsBySeverity.high;
        acc.critical += curr.issueCountsBySeverity.critical;
        return acc;
      },
      {name: projectName, high: 0, critical: 0, url: projectUrl},
    );
}

function getProjectIdentifier(projectPrefix: string, projectName: string) {
  if (projectName === 'master') {
    return getMasterProjectIdentifier(projectPrefix, projectName);
  }
  return getDockerProjectIdentifier(projectPrefix, projectName);
}

function getDockerProjectIdentifier(projectPrefix: string, projectName: string) {
  return `${projectPrefix}:${projectName}`;
}

function getMasterProjectIdentifier(projectPrefix: string, projectName: string) {
  return `${projectPrefix}(${projectName})`;
}

function formatResults(projectSummary: ReturnType<typeof getProjectSummary>, idx: number) {
  const {name, critical, high, url} = projectSummary;
  return `${idx + 1} <${url}|${name}: ${critical} Critical, ${high} High>`;
}

function getProjectUrl(projectPrefix: string, projectName: string, projectOrigin: string) {
  return `https://app.snyk.io/org/team-optimize/reporting?context%5Bpage%5D=issues-detail&project_target=${projectPrefix}&project_origin=${projectOrigin}&target_ref=${projectName}&issue_status=Open&issue_by=Severity&table_issues_detail_cols=SCORE%257CCVE%257CCWE%257CPROJECT%257CEXPLOIT%2520MATURITY%257CAUTO%2520FIXABLE%257CINTRODUCED&table_issues_detail_sort=%2520FIRST_INTRODUCED%2520DESC&issue_severity=High%257CCritical`;
}
