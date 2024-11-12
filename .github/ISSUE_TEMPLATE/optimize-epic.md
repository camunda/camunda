---

name: Optimize Epic
about: This issue is used to track the progress of an Optimize Epic
title: 🎯
labels: ["kind/epic", "component/optimize"]
assignees: ""

---

### Summary

<!-- Add links to related issues or other resources  -->
- Product Hub issue:
- PDP Steps tracking doc:

### Breakdown

<!-- A breakdown of tasks that need to be completed in order for this to be ready for review. -->
<!--
- [ ] #123
- [ ] Step X
-->

```[tasklist]
### Pull Requests
```

## Epic Lifecycle

For managing the issue lifecycle, please use the workflow commands. You can see the available
commands by writing `/help` as a comment on this issue.

### Review

#### Engineering Review

- [ ] All code targeting the main branch has been reviewed by at least one Engineer
- [ ] The PR targeting the main branch has been approved by the reviewing Engineer
- [ ] If the API has changed, the API documentation is updated
- [ ] All other PRs (docs, controller etc.) in the breakdown have been approved

#### QA Review

- [ ] The change is implemented as described on all target environments/versions/modes
- [ ] The documentation changes are as expected

#### PM Review

- [ ] The changes satisfy the requirements for the described epic
- [ ] The documentation sufficiently describes the new/changed behaviour
- [ ] Test data is available in order to demo this change, if necessary

### Completion

- All Review stages are successfully completed
- [ ] All associated PRs are merged to the main branch(es) and stable branches
- [ ] The correct version labels are applied to the issue

