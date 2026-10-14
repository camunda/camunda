import { writeFileSync } from 'node:fs';
import * as core from './gha';
import { parseDryRun } from './apply/index';
import { runApply } from './apply/run';
import { fetchIssueStates, fetchLabelledNumbers } from './apply/reader';

/**
 * The `apply` entrypoint, dry-run only: compares the generator's plan with
 * what zcl labelled and writes the report to the job summary. Read-only
 * token; there is no code path here that writes to GitHub.
 */
async function run(): Promise<void> {
  const [owner, repo] = (process.env.GITHUB_REPOSITORY ?? '').split('/');
  if (!owner || !repo) throw new Error(`GITHUB_REPOSITORY must be set to "owner/repo", got "${process.env.GITHUB_REPOSITORY ?? ''}".`);
  const token = core.getInput('token', { required: true });
  const inputDir = core.getInput('input-dir') || '.';

  const result = await runApply(
    {
      inputDir,
      dryRun: parseDryRun(core.getInput('dry-run')),
      generateOutcome: core.getInput('generate-outcome'),
    },
    {
      labelled: (label) => fetchLabelledNumbers(token, owner, repo, label),
      issueStates: (numbers) => fetchIssueStates(token, owner, repo, numbers),
    },
  );

  for (const warning of result.warnings) core.warning(warning);
  await core.summary.addRaw(result.markdown).write();
  if (result.divergence) writeFileSync(`${inputDir}/apply-divergence.json`, JSON.stringify(result.divergence, null, 2));
}

run().catch((err) => core.setFailed(err instanceof Error ? err.message : String(err)));
