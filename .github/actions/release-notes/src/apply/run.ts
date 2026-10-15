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
  /** Whether the generator's plan was compared with an observed baseline. False
   *  when the run was skipped or no issue carried the label. */
  readonly compared: boolean;
  /** The full plan for the run artifact; absent when the run was skipped. */
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
  const unread = wanted.filter((number) => !issueFacts.has(number));
  if (unread.length > 0) {
    warnings.push(`The state of ${unread.length} of ${wanted.length} issues could not be read (${unread.slice(0, 5).map((number) => `#${number}`).join(', ')}${unread.length > 5 ? ', …' : ''}), so the comment comparison is incomplete.`);
  }
  const truncated = [...issueFacts].filter(([, state]) => state.commentsTruncated).map(([number]) => number);
  if (truncated.length > 0) {
    warnings.push(`${truncated.length} issues have more comments than were read (${truncated.slice(0, 5).map((number) => `#${number}`).join(', ')}${truncated.length > 5 ? ', …' : ''}), so an existing comment may be missed.`);
  }
  return { markdown: renderReport(plan), compared: !plan.baselineEmpty, divergence: plan, warnings };
}

function skipped(reason: string): ApplyResult {
  return { markdown: renderDegradedReport(reason), compared: false, warnings: [`Apply dry run skipped: ${reason}`] };
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
