# Backward compatibility

This policy answers two questions:

1. [Is a change breaking?](#is-a-change-breaking)
2. [Is a breaking change allowed in this release?](#is-a-breaking-change-allowed)

In short: we don't allow changes that break user space, apart from very rare exceptions.

## Why we don't break user space

Customers run their business-critical processes on Camunda. They trust that an upgrade doesn't change how those processes behave, and a surprise breaks that trust. Additionally, small breaking changes add up for them: each one is small, but the effort they need to put into it compounds.

## Is a change breaking?

A change **breaks user space** when something a user built or does **behaves differently after an upgrade**. For example, a customer's processes no longer work like before.

What users build or do includes, but is not limited to:

- Processes, decisions and FEEL expressions, and the job workers, clients and tests that run them.
- Shared BPMN, DMN and FEEL patterns, element templates and connectors.
- Actions on running process instances, such as resolving incidents, retrying jobs, changing variables, modifying and migrating instances, and batch operations.
- Configuration, upgrades, and the metrics that operators monitor and alert on.
- Custom exporters, which read the records.

A change does **not** break user space when nobody could depend on the old behavior:

- **Unusable**: it failed, crashed, hung, or produced nothing.
- **Not observable**: users could not see it, for example because it looked the same as other behavior.
- **Nondeterministic**: it varied between runs. Behavior that is only hard to predict doesn't count: if a process behaves the same every time for the same model and input, users can rely on it.
- **Never released**: for example, it only existed in an alpha. Alphas can't be upgraded today, so no user can depend on it.
- **A regression we just released**: it came with the last release, and we revert it in the next patch. Users had no time to rely on it.

**The contract is the current behavior**, not what is documented or designed. So a bug fix, or a change that makes the engine match the documentation, still breaks user space when users could rely on the current behavior.

## Is a breaking change allowed?

A change may break user space only as an exception, when all of these hold:

1. **No way without a break.** We always first look for a way to avoid the break, such as adding behavior or an [opt-in](#ways-to-opt-in). There is none, or it would come too late for an urgent fix.
2. **The fix is one of these:**
   - In a patch or a minor: reverting a regression from before the last release, fixing a [critical](https://github.com/camunda/camunda/blob/main/CONTRIBUTING.md#severity-and-likelihood-bugs) bug, or fixing a security flaw when meeting the [vulnerability SLO](https://github.com/camunda/isms/blob/main/standards/vulnerability_slos.md) requires it.
   - In a minor only: fixing a bug that risks data loss or corruption.
3. **The harm the fix prevents outweighs the harm the break causes.** See [weighing the harms](#weighing-the-harms).
4. **The team's engineering manager approves.** The EM can ask for help higher up, as for [#55491](https://github.com/camunda/camunda/issues/55491).
5. **It is announced as a breaking change**, in the release announcements. The announcement tells users how to find out whether they are affected, such as which BPMN elements, settings or API calls to look for, and what to change. The team also tells customer success, so they can reach affected customers.

Making the engine match the documentation or the original design is never an exception.

The exceptions in this policy never allow breaking the [public API](https://docs.camunda.io/docs/reference/public-api/), because that is a contractual promise. The C8 API governance team owns what happens there.

### Weighing the harms

The fix helps users who hit the bug. The break hurts users who relied on the old behavior. For each side, consider:

- **Who is affected, and how many.** List every way users can reach the behavior, and map each to [what users build or do](#is-a-change-breaking). For example, the impact list of [#49365](https://github.com/camunda/camunda/issues/49365) missed multi-instance call activities. A change to a shared pattern or connector can break many teams at once. Prefer data, such as usage numbers or support cases, over arguments.
- **How badly.** For example: lost, corrupted or exposed data, failing processes, or work to adapt.
- **Whether they can act.** Users can adapt to an announced break. They can't act on silent data loss.
- **When they get the change.** A patch reaches users within weeks, on SaaS through auto-updates, so a break leaves them no time to prepare. A fix that waits for the next minor leaves users exposed until then.

### Ways to opt in

Every opt-in costs development effort, API surface and code complexity, and both behaviors need support for a long time. Users also get one more option to understand in Modeler, the API or the configuration. The team picks the way that fits the change:

- **A setting per model**, when the change affects how processes run. Existing models keep the old behavior. Modeler can set the new behavior on new elements and lint existing ones, and each process moves on its own schedule.
- **An option on a call**, when the change affects one kind of call. For example, a parameter on a Camunda extension FEEL function, a flag on an API request, or a new method on the exporter API.
- **A config option.** It is one choice for the whole cluster, so all tenants move at once. On SaaS, users can't change it themselves unless Console offers it as a cluster setting. The brokers read it only when they restart.

A fix behind an opt-in that is off by default doesn't break user space, so it is not an exception. It can reach users before the next minor as a backport. But it adds new functionality to a patch, so the release notes must call that out. Whether to backport it, and who decides, follows Camunda's backporting policy. Until that policy exists, the team decides, weighing the costs of the opt-in above against the harm the change prevents.

### Asking for an exception

Document the case on the issue, so the decision and its reasons stay easy to find. Cover each condition:

- **Ways without a break**: the alternatives you considered, such as an opt-in, with their pros and cons, and why none of them works.
- **The kind of fix**: which kind of fix it is, and in which releases it ships.
- **The harms**: your weighing of both sides, as in [weighing the harms](#weighing-the-harms).
- **The announcement**: a draft that tells users how to find out whether they are affected, and what to change.

The team's EM approves on the issue. Add the `BREAKING CHANGE` label to the PR that makes the change. The release notes then show it as a breaking change, and searching for the label lists all exceptions. Before the release, also post the announcement in [#ask-customer-success](https://camunda.slack.com/archives/C06FTUZ6430), so customer success can prepare affected customers before they upgrade.

## Examples

- **Not breaking**: [#64500](https://github.com/camunda/camunda/pull/64500). Canceling a process instance that is already terminating was rejected with NOT_FOUND. Users could not tell a terminating instance from a terminated one, so they could not depend on it ([review](https://github.com/camunda/camunda/pull/64500#pullrequestreview-5376090525)).
- **Breaking, allowed in a minor**: [#55491](https://github.com/camunda/camunda/issues/55491). A variable written back with an unchanged value propagated past the scope that held it, and could overwrite a different value higher up. The fix prevents silent data loss, but processes may rely on the bug. So it ships in 8.11 only, announced as a breaking change.
- **Breaking, allowed in a minor, but should have been announced**: [#36513](https://github.com/camunda/camunda/issues/36513). The 8.8 minor fixed broken security of task assignments in Tasklist, and assignments that used to work were now rejected, which surprised users ([comment](https://github.com/camunda/camunda/issues/36513#issuecomment-3294122835)). Tasklist isn't part of the engine, and the example is here for its behavior, not its API.
- **Breaking, not allowed in a patch**: [#33952](https://github.com/camunda/camunda/issues/33952). Some process instances started from the none start event instead of the signal start event. The fix went into patches, but the bug was not critical (severity high).
- **Breaking, not allowed: matching the docs**: [#49365](https://github.com/camunda/camunda/issues/49365). A call activity with `propagateAllParentVariables=false` and no input mappings doesn't pass its local variables to the child, although the docs say it does. A [fix](https://github.com/camunda/camunda/pull/67564) that copies them changes what existing processes pass, for example the loop variables of a multi-instance call activity. Instead, the new behavior becomes an opt-in setting on the call activity.

## Scope

The policy covers engine behavior, wherever its code lives. Besides what [users build or do](#is-a-change-breaking), that includes the data that exporters write, the gateway and service layer on the way between the API and the engine, the processors and appliers for Identity entities, and the results of the secondary storage queries that batch operations and history deletion use to find the items to act on.

It does not cover REST API compatibility, which the C8 API governance team owns ([REST API endpoint guidelines](https://github.com/camunda/camunda/blob/main/docs/rest-api-endpoint-guidelines.md)).

## Questions

The team that makes the change applies this policy. If you're unsure whether a change breaks user space or is allowed, ask in [#top-c8-engine-governance](https://camunda.slack.com/archives/C0BPZFRT19N). The reasoning and sources behind the policy are in [camunda/pdp-3782-engine-governance](https://github.com/camunda/pdp-3782-engine-governance/blob/main/docs/policies/backward-compatibility.md), for Camunda staff.
