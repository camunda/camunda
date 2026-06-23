# Release Train

## Scope and Boundaries

This page documents the C8 Release Train and the SaaS generation flow. It covers how patch, alpha, and minor release trains are started and coordinated, how the train progresses across component releases, SaaS generation, tests, and rollout, and how to recover when the train is already in progress and something breaks.

The Monorepo release (camunda/camunda) is a step within the C8 Release Train. The train can only proceed once the Monorepo artifacts it depends on are confirmed as released. See [Monorepo Release](./release-monorepo.md) for how to produce those artifacts.

For now, the agreed home for these docs is the existing release docs site under `camunda.github.io/camunda/release/`.

**References**

* [C8 Release Train](https://lamppost.camunda-it.rocks/handbook/index.html?continue#departments/products/product-management/release-management-process/c8-release-train/index.md)
* [Basic Operational Instructions](https://lamppost.camunda-it.rocks/handbook/index.html?continue#departments/products/product-management/release-management-process/c8-release-train/basic-operational-instructions/index.md)
* [Release Process](./index.md)

## Release Train Repository

The release train BPMN and supporting code lives in [camunda-release/release-train](https://github.com/camunda/camunda-release). Development via Camunda Modeler is no longer supported — all contributions go through GitHub pull requests.

To contribute:

1. Open a pull request to `camunda-release/release-train`.
2. Test your changes on the dev cluster before merging (see [Testing Changes on Dev](#testing-changes-on-dev)).
3. Roll back any dev overrides before merging to `main`.

## Testing Changes on Dev

Use this to validate release train changes before they reach prod.

1. Create a branch from `main` in `camunda/camunda-release`.
2. Apply dev overrides to your branch.
3. Commit and push your branch.
4. Deploy to dev: go to **Actions → Release Train - Deploy → Run workflow**, select your branch, and set cluster to `dev`.
5. Validate in the dev cluster Operate and Tasklist.
6. Roll back the dev overrides before merging to `main`.

**Example:** [camunda-release PR #100](https://github.com/camunda/camunda-release/pull/100/changes/ce63df782ab2685a408e73370b5e0267e2063fba)

> **Coordinate before deploying.** Each deploy creates a new version of every changed process. If two people deploy different branches, the latest deploy wins. Align in [`#c8-release-train-development`](https://camunda.slack.com/archives/CHY2S7KDJ) before you deploy.

> **Lint enforcement.** The lint workflow blocks any PR to `main` that contains dev-only values (dev cluster ID, test Slack channel, `development=true`). Dev overrides cannot be merged to prod.

## Happy-Path Flow

At a high level, the C8 Release Train starts from a scheduled or ad-hoc trigger, collects the target release lines and base generations, coordinates the component releases (including the Monorepo release), creates SaaS generations for dev/int/prod, runs release validation and rollout work, and ends with support communication and the remaining release wrap-up.

A concise happy-path view:

1. Start the train from the release-train trigger process, selecting the release type and base generations for the lines being released.
2. Release or confirm the required component versions as the train progresses through the user tasks.
3. Once the component releases are ready, create the SaaS generation for dev/int and continue with rollout-related user tasks.
4. Complete the test and rollout steps, then continue with the remaining self-managed and communication steps until the train is done.

For ad-hoc patch releases:

* Patches can be triggered any time during the month.
* They should be based on the previous generation for that line.
* Support notification should be enabled when starting the train.

To skip a component that does not need a new release, fill in that component's release form in Tasklist with the most recently released version, mark it as released, and submit. See [Skipping components for an ongoing release train](https://lamppost.camunda-it.rocks/handbook/index.html?continue#departments/products/product-management/release-management-process/c8-release-train/basic-operational-instructions/index.md~skipping-components-for-an-ongoing-release-train) in the Lamppost handbook.

## Rules and Invariants

Use these as short operational rules that help readers decide what is normal and what is drift.

* There must not be two concurrent release trains for the same major/minor version.
* The C8 Release Train can only proceed once the Monorepo artifacts it depends on are confirmed as released.
* Patch releases can be done on demand, but they should still be based on the previous generation for that line.
* Only patch releases can be triggered ad-hoc. Alphas and minors always follow the monthly release cadence — a team requesting an ad-hoc alpha must wait for the next monthly cycle.
* SaaS generation create/change operations are restricted to `@c8-release-train-manager`, coordinated in [`#top-c8-release-train`](https://camunda.slack.com/archives/C03NFMH4KC6).
* The expected upgrade path for a generation is: to the latest patch of the same minor; if already on the latest patch, to the latest patch of the next minor. Alpha generations cannot be upgraded to — their `updatableFrom` list is expected to be empty.
* Upgrade path configuration must be consistent across dev, int, and prod. A path configured in one environment but not others is drift and should be investigated.
* If an already running train needs to switch to a rereleased component version, patch the running train by updating the old version references in the process variables — always `releaseSteps` and the relevant component version vars, plus `baseVersions`.
* If part of the process must be repeated, process-instance modification can move the token back, but this is dangerous and should be reviewed before doing it.
* If there is a serious issue during release execution, open an incident rather than trying to normalize the failure informally.

## SaaS Generation

This section covers the SaaS-specific part of the C8 Release Train: creating the new dev/int generation, running the SaaS rollout tasks, and verifying that the resulting generation and upgrade paths match the intended release state.

The SaaS Generation section stays operational and happy-path focused. Incident recovery patterns such as replaying the SaaS flow after a flawed generation was created belong in [Troubleshooting](#troubleshooting).

### What this part of the train does

On the happy path, the release train first collects the target release lines and component versions, then creates the new generation for dev/int, continues with the rollout tasks, and later proceeds with the remaining release and communication steps.

In practice, this part of the process is where the train turns released component versions into a concrete SaaS generation for the target line.

### Inputs and prerequisites

Before the SaaS Generation part runs, make sure:

* The train was started with the correct base generation for the release line.
* The intended new generation name was provided in the train start form.
* The required component versions were released or explicitly reused if a component is being skipped.
* Support notification was enabled when the train was started.

### Ownership and coordination

SaaS generation create/change operations are restricted to `@c8-release-train-manager` and must be coordinated in [`#top-c8-release-train`](https://camunda.slack.com/archives/C03NFMH4KC6).

### What to verify after SaaS Generation

After the generation is created and the rollout proceeds, verify:

* The fresh generation exists with the expected name.
* The generation contains the intended component versions.
* The upgrade paths point to the intended safe generations.
* Stable and support channel references match the intended release state.

If any of these checks fail, continue in [Troubleshooting](#troubleshooting) rather than trying to normalize the state informally.

### Support notification timing

Enable support notification when starting the train unless there is an explicit reason not to. The "Send support notification?" toggle is in the train initiation form.

Additional timing notes:

* Ask-support notifications must be sent as soon as all releases in the trigger batch are complete (SM and SaaS), excluding the SaaS autoupgrade step — approve the ask-support message send-out as soon as everything else is done.
* **Autoupgrades** are the lowest-priority step and can be done after the rest of the train completes — typically the next business day if no blockers exist.
* **Alpha releases** do not include SaaS autoupgrades.
* If autoupgrades are pending and not yet triggered, they do not block the train from being considered released. Communicate the pending status clearly in the train thread.
* If an active incident is causing a release delay, you do not need to proactively communicate ETAs to stakeholders — they will receive `ask-support` messages when the train completes.

## Troubleshooting

### Flawed generation already created in dev/int

Use this when the Monorepo is already released with a flaw, the current train already created dev/int SaaS generations with the flawed component versions.

**Symptoms**

* The train is still in progress, but dev/int generations already exist with flawed component.
* The flawed generation is already used by clusters, so deleting it is not an option.
* The issue has not reached prod yet.

**Prerequisites**

* The Monorepo fix must be released before you can patch the train. The version you will patch into the process variables is the Monorepo re-release version — confirm it is available before changing any variables.

**Recommended recovery path**

1. Do not silently reuse the flawed generation.
2. Rename the broken generation in dev/int to make it explicit that it must not be used, for example `8.9+gen8 BROKEN DO NOT USE`. Be aware that clusters on the previous generation (e.g. `8.8+gen24`) will temporarily have no valid upgrade target — see Notes below.
3. Patch the running train variables with the corrected versions, using the version number from the Monorepo re-release. See [Patching running train variables](#patching-running-train-variables) for the exact steps.
4. Move the BPMN token back to the `SaaS Generation Rollout Dev/Int` step so the corresponding flow is repeated and a fresh generation with the original name is created from the corrected variables.
5. After the fresh generation is created, repair upgrade paths and channels/stable references:
   * Remove upgrade paths to the flawed generation first if not already done.
   * Restore upgrade paths to the latest unflawed generation as soon as possible. If needed, temporarily add the previous patch as a backup path while the corrected generation is being rebuilt.
   * Make sure stable and support channels reflect the same intended safe state before proceeding with the rest of the train.

**Notes and watch-outs**

* Treat process-instance modification with care. Moving tokens or changing variables can create inconsistencies if preconditions are not met — have another engineer review the plan when possible.
* Generations used by clusters cannot be deleted. Prefer rename-and-recreate over delete-and-recreate.
* A bad generation is a stop sign. Do not proceed until the state is understood or resolved.
* Do not remove upgrade paths from the previous generation to the flawed generation without first providing a safe alternative. Customers on the previous generation may be left with no valid upgrade target. Either redirect those paths to a known-good generation as a temporary target, or minimize the window by moving quickly to restore paths to the fresh generation.
* Alpha generations cannot be upgraded to — an empty `updatableFrom` list on an alpha generation is expected, not a sign of drift.

**Where to check the audit trail**

* Dev and int generation audit logs: [`#cloud-admin-preprod`](https://camunda.slack.com/archives/C01EXB99MJ7)
* Prod generation audit logs: [`#cloud-admin`](https://camunda.slack.com/archives/C01EGCSLGTZ)

**Worked example: 8.8+gen25**

During the June 2026 incident, a critical bug was found in the Monorepo after `8.8+gen25` was already created in dev/int with the flawed version `8.8.26`. The recovery looked like this:

0. Waited for the Monorepo re-release to complete and confirmed that `8.8.27` was available before touching any train variables.
1. Renamed `Camunda 8.8+gen25` → `Camunda 8.8+gen25 BROKEN DO NOT USE` in dev and int. Clusters on `8.8+gen24` were temporarily left without an upgrade path — this window was minimized by moving quickly through the next steps.
2. Patched process variables: bumped `monorepoVersion`, `zeebeVersion`, `operateVersion`, `tasklistVersion`, `identityVersion`, `optimizeVersion`, and `baseVersions` from `8.8.26` to `8.8.27`. Also updated `releaseSteps` to reflect the new versions.
3. Moved the BPMN token back to `SaaS Generation Rollout Dev/Int`.
4. The train recreated `Camunda 8.8+gen25` with the corrected versions.
5. Repaired upgrade paths so that `8.8+gen24 → 8.8+gen25 → 8.9+gen8` were correctly wired, and updated stable/support channels. Stable versions restored across lines were `8.9+gen6`, `8.8+gen23`, and `8.7+gen28` — ensuring customers had a safe upgrade target while the corrected trains were being rebuilt.

**References**

* [Incident discussion and recovery approach](https://camunda.slack.com/archives/CHY2S7KDJ/p1780928211613299?thread_ts=1780674684.007179&cid=CHY2S7KDJ)
* [8.8+gen25 release thread](https://camunda.slack.com/archives/C03NFMH4KC6/p1781115581813819?thread_ts=1780994207.740159&cid=C03NFMH4KC6)

### Patching running train variables

Use this when a component version changes after the train has already started and variables in the running process instance need to be updated. The base procedure — finding the instance in Operate, amending variables, and saving — is documented in the Lamppost handbook:

* [Changing component version variables for an ongoing release train](https://lamppost.camunda-it.rocks/handbook/index.html?continue#departments/products/product-management/release-management-process/c8-release-train/basic-operational-instructions/index.md~changing-component-version-variables-for-an-ongoing-release-train)
* [Changing a version of an already released component](https://lamppost.camunda-it.rocks/handbook/index.html?continue#departments/products/product-management/release-management-process/c8-release-train/basic-operational-instructions/index.md~changing-a-version-of-an-already-released-component)

**Monorepo-specific additions**

1. Before changing anything, leave a message in the Slack thread of the release train:
   > ⚠️ I'm patching the release train to use \<component\> version a.b.c instead of version a.b.d
2. Beyond `<component>Version` and `baseVersions`, also update `releaseSteps` — the JSON object containing per-component version info that the handbook examples omit.
3. For a **Monorepo re-release** specifically, update all of:
   * `monorepoVersion`, `zeebeVersion`, `operateVersion`, `tasklistVersion`
   * `identityVersion` — **not** `identityManagementVersion`; these are different components
   * `releaseSteps` and `baseVersions`
   * plus any other component vars also rereleased (e.g. `optimizeVersion` for 8.8+)

**Notes**

* `releaseSteps` and `baseVersions` are the two variables most commonly forgotten. Both must be updated.
* `identityVersion` is the orchestration-cluster identity component; `identityManagementVersion` is the separate Identity Management component — always update `identityVersion` for Monorepo re-releases.
* The later you are in the train flow, the more likely it is safer to abandon and restart with a new train rather than patching.

### Generation rollout error: cluster not fully created yet

During SaaS generation rollout, some clusters may reject the generation update with an admission webhook error:

> updates to generation are not possible as cluster is not fully created yet

This means the cluster was still being provisioned when the generation update was applied. The controller rejects the update to avoid leaving the cluster in an inconsistent state.

**What to do**

* Identify which clusters triggered the error and check whether they belong to external customers.
* Open an incident for each affected customer cluster.
* Check whether the cluster completed provisioning and whether the generation update needs to be retried.

**Notes**

* Internal test clusters (e.g. Controller Team Enterprise Test) typically have lower urgency than customer clusters.
* A similar error occurred during the 8.9+gen4 → 8.9+gen5 transition; see incident `inc-dead-partition-due-to-missing-log-entries` for prior context.

### Clusters with missing health or deleted status during bulk auto-upgrade

During the prod rollout, you may see clusters with an unknown/missing health status or a deleted status included in a bulk auto-upgrade operation. This is expected behavior — do not treat it as a stop condition.

**How auto-upgrade handles these clusters**

* **Unhealthy or unknown-health clusters** are included in bulk auto-upgrades. The rationale is that upgrading may help resolve the unhealthy state.
* **Sleeping clusters** are excluded from auto-upgrades automatically.
* **Deleted clusters** appearing in a bulk operation count should be investigated — they are likely already gone and will not actually be upgraded.

**Notes**

* A cluster without a reported health status is not automatically a blocker — but investigate before assuming it is safe to upgrade.
* If a deleted cluster appears in the operation, confirm it is genuinely deleted and not in an ambiguous state before continuing.
* The sleeping-cluster exclusion is enforced by the controller; no manual action is needed.

**References**

* [ask-controller thread confirming behavior](https://camunda.slack.com/archives/C051AA63QV8/p1781596129229369?thread_ts=1781527382.832229&cid=C051AA63QV8)

### Recovery verification checklist

Use this after patching a running train and replaying the SaaS generation or rollout steps.

- [ ] Confirm the fresh generation exists with the expected original name.
- [ ] Confirm the fresh generation contains the corrected component versions.
- [ ] Confirm the train is continuing against the fresh generation, not the renamed broken one.
- [ ] Confirm upgrade paths point to the intended safe generations.
- [ ] Confirm stable and support channels point to the intended generations.
- [ ] Confirm the dev/int SaaS rollout completed successfully.
- [ ] If the old broken generation is still visible, confirm it is clearly renamed and not the active target for the train.

## FAQ

### 1. Where does the Monorepo release fit in the C8 Release Train?

The Monorepo release is a step within the C8 Release Train, not a separate process. The train waits for the Monorepo to produce its core backend artifacts, then continues with the downstream component steps, SaaS generation, release tests, rollout, and support communication.

As a rule of thumb:

* If the issue is about producing or validating Monorepo artifacts → use [Monorepo Release](./release-monorepo.md).
* If the issue is about train coordination, generations, upgrade paths, channels, release-train variables, or support-notification timing → use this page.

### 2. What should I do if a flawed generation was already created in dev/int?

Rename the broken generation to `BROKEN DO NOT USE`, patch the train variables (including `releaseSteps` and `baseVersions`), move the BPMN token back to the SaaS generation or rollout step, and verify the fresh generation before continuing.

See [Flawed generation already created in dev/int](#flawed-generation-already-created-in-devint) in Troubleshooting for the full procedure and the 8.8+gen25 worked example.

### 3. How do I know which part of the train must be replayed?

Look at the current train state first. If Monorepo is already completed but the SaaS generation or SaaS rollout part still needs to be rebuilt, replay the SaaS side rather than the Monorepo release itself. In the release-train flow, dev/int generation creation and the later SaaS generation release are separate parts of the process.

### 4. Can I retry or skip parts of the BPMN flow?

Yes, but carefully. Process-instance modification in Operate lets you retry earlier steps or skip later ones. The risk is that moving tokens or changing variables can create inconsistencies if preconditions are not met.

A safe rule is:

* First fix the underlying variables or external state.
* Only then move the token to the exact step that needs to be repeated.
* If in doubt, get another release engineer to review the plan before modifying the instance.

**References**

* [Patching running train variables](#patching-running-train-variables)

### 5. When should I open an incident instead of continuing manually?

If there is a serious issue during release execution, open an incident instead of trying to normalize the failure informally.

That is especially true when:

* The release state is unclear and different engineers have conflicting interpretations.
* Customer upgradeability is affected.
* Multiple release lines are impacted at once.
* The fix requires manual process-instance modification, generation replacement, or channel/path repair.
