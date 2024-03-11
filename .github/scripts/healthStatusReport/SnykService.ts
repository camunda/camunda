/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {fetchUrl} from './services';
import {SnykProject, SnykProjectsResponse} from './types';

export class SnykService {
  constructor(
    private readonly snykToken: string,
    private readonly orgId: string,
    private readonly apiVersion: string,
    private readonly excludedVersions: string[] = [],
  ) {}

  public fetchSnykProjects = async () => {
    return (
      (
        await fetchUrl<SnykProjectsResponse>(
          `https://api.snyk.io/rest/orgs/${this.orgId}/projects?version=${this.apiVersion}&limit=100`,
          `token ${this.snykToken}`,
        )
      ).data || []
    ).filter(
      (project) =>
        !this.excludedVersions.some((excludedVersion) =>
          project.attributes.name.includes(excludedVersion),
        ),
    );
  };

  public getHighestDockerVersions = (projects: SnykProject[]): string[] => {
    const camundaOptimizeProjects = projects.filter((project) =>
      project.attributes.name.includes('camunda/optimize:'),
    );
    const uniqueVersions = this.getUniqueProjectVersions(camundaOptimizeProjects).sort(
      this.sortSnykBranches,
    );
    return this.getHighestProjectVersions(uniqueVersions);
  };

  private sortSnykBranches = (firstBranch: string, secondBranch: string): number => {
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
  };

  private getUniqueProjectVersions = (projects: SnykProject[]): string[] => {
    const projectVersions = new Set<string>();
    projects.forEach((project) => projectVersions.add(project.attributes.name.split(':')[1]));
    return [...projectVersions];
  };

  public getHighestProjectVersions = (projectVersions: string[]) => {
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
  };
}
