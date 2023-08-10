/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import fetchUrl from './fetchUrl';
import {readConfig} from './readConfig';

const {workflows = []} = readConfig();

type Branch = {
  ref: string;
};

type Run = {
  html_url: string;
  name: string;
  head_branch: string;
  status: string;
  conclusion: string;
};

type CiResponse = {
  workflow_runs: Run[];
};

export default async function getCiResults(): Promise<string> {
  const ciBranches = await getCiBranches();
  const workflowRuns = await Promise.all(
    workflows
      .map((workflow) => {
        if (typeof workflow === 'string') {
          return fetchWorkflow(workflow, 'master');
        }
        const {name, checkBranches} = workflow;
        return checkBranches
          ? ciBranches.map((branch) => fetchWorkflow(name, branch))
          : fetchWorkflow(name, 'master');
      })
      .flat(),
  );

  const lastCompletedRuns = workflowRuns
    .map((workflow) => workflow.workflow_runs?.find((run) => run.status !== 'in_progress'))
    .filter((run): run is Run => run !== undefined);

  const message = lastCompletedRuns
    .map(
      (run, idx) =>
        `${idx + 1} ${isRunSuccessful(run) ? '🟢' : '🔴'} <${run.html_url}|${run.name} (${
          run.head_branch
        })>`,
    )
    .join('\n');

  return message;
}

async function getCiBranches() {
  const maintenanceBranches = new Set<string>();
  (await fetchBranches())
    .map(branchToBranchName)
    .filter((name) => name !== undefined)
    .sort(sortBranches)
    .forEach((name) => maintenanceBranches.add(name));
  return ['master', ...maintenanceBranches];
}

async function fetchBranches() {
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

function sortBranches(firstBranch: string, secondBranch: string) {
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

async function fetchWorkflow(workflow: string, branch?: string) {
  return fetchUrl<CiResponse>(
    `https://api.github.com/repos/camunda/camunda-optimize/actions/workflows/${workflow}.yml/runs${
      branch ? `?branch=${branch}` : ''
    }`,
    `token ${process.env.GITHUB_TOKEN}`,
  );
}

function isRunSuccessful(run: Run) {
  return run.status === 'completed' && run.conclusion === 'success';
}
