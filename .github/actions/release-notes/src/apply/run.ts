import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { parseGeneratorFiles, planApply, renderDegradedReport, renderReport } from './index';
import type { IssueState } from './index';
import type { Labelled } from './reader';

export interface ApplyInputs {
  readonly inputDir: string;
  readonly dryRun: boolean;
  /** Outcome of the generate step; empty when the caller did not pass one. */
  readonly generateOutcome: string;
}

/** Reads only. The real executor will be a second dependency, not a flag on these. */
export interface ApplyDeps {
  labelled(label: string): Promise<Labelled>;
  issueStates(numbers: readonly number[]): Promise<ReadonlyMap<number, IssueState>>;
}

export interface ApplyResult {
  readonly markdown: string;
  /** The full plan for the run artifact; absent when nothing was compared. */
  readonly divergence?: object;
  readonly warnings: readonly string[];
}

export async function runApply(inputs: ApplyInputs, deps: ApplyDeps): Promise<ApplyResult> {
  if (!inputs.dryRun) throw new Error('Real apply is not implemented yet; run with dry-run=true.');
  if (inputs.generateOutcome !== '' && inputs.generateOutcome !== 'success') {
    return skipped(`the generate step outcome was ${inputs.generateOutcome}, so there are no files to compare.`);
  }

  let files;
  try {
    files = parseGeneratorFiles({
      labels: readInput(inputs.inputDir, 'labels.json'),
      comments: readInput(inputs.inputDir, 'comments.json'),
      changelog: readOptionalInput(inputs.inputDir, 'changelog.json'),
    });
  } catch (error) {
    return skipped(error instanceof Error ? error.message : String(error));
  }

  const labelled = await deps.labelled(`version:${files.labels.version}`);
  // State and comments are read for the generator's issues (comment rule) and for
  // zcl-only ones (hint), once each, in batches.
  const wanted = [...new Set([...files.labels.issues, ...labelled.issues])];
  const issueFacts = await deps.issueStates(wanted);

  const plan = planApply(files, { zclIssues: labelled.issues, zclPullRequests: labelled.pullRequests, issueFacts });
  const warnings = plan.baselineEmpty ? [`No issues carry version:${plan.version}, so there was nothing to compare with.`] : [];
  return { markdown: renderReport(plan), divergence: plan, warnings };
}

function skipped(reason: string): ApplyResult {
  return { markdown: renderDegradedReport(reason), warnings: [`Apply dry run skipped: ${reason}`] };
}

function readInput(directory: string, name: string): string {
  try {
    return readFileSync(join(directory, name), 'utf8');
  } catch {
    throw new Error(`${name} not found in ${directory}.`);
  }
}

/** changelog.json only adds facts to hints, so its absence is not a reason to skip. */
function readOptionalInput(directory: string, name: string): string | undefined {
  try {
    return readFileSync(join(directory, name), 'utf8');
  } catch {
    return undefined;
  }
}
