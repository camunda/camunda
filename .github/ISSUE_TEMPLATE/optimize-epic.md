---
name: Optimize Epic
about: This issue is used to track the progress of an Optimize Epic
title: ''
labels: type:epic
assignees: ''
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

### Review

#### Review Resources

<!-- When in review, the resources to be used for review should be listed here) -->

- Feature PR: {Link to PR targeting the main branch}
- Preview environments: {Link(s) to preview environments}

When all the resources are available, please let the Engineering review know via a comment that this
issue is ready for review and assign them to the Feature PR.

#### Engineering Review

- [ ] All code targeting the main branch has been reviewed by at least one Engineer
- [ ] The PR targeting the main branch has been approved by the reviewing Engineer
- [ ] If the API has changed, the API documentation is updated
- [ ] All other PRs (docs, controller etc.) in the breakdown have been approved

When all stages are complete, please move the issue to the "QA Review" status. If any stages are
incomplete, please move it back to "In Progress" and let the author know via a comment.

#### QA Review

- [ ] The change is implemented as described on all target environments/versions/modes
- [ ] The documentation changes are as expected

When all stages are complete, please move the issue to "PM Review" status and let the PM
know via a comment. If any stages are incomplete, please move it back to "In Progress" and let the
author know via a comment explaining the reasons why.

#### PM Review

- [ ] The changes satisfy the requirements for the described epic
- [ ] The documentation sufficiently describes the new/changed behaviour
- [ ] Test data is available in order to demo this change, if necessary

When all stages are complete, please move the issue to "Ready to Complete" status and let the author
know via a comment. If any stages are incomplete, please move it back to "In Progress" and let the
author know via a comment explaining the reasons why.

### Completion

- All Review stages are successfully completed
- [ ] All associated PRs are merged to the main branch(es) and maintenance branches
- [ ] The correct version labels are applied to the issue
