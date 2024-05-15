---
name: Optimize Bug report
about: Report a problem and help us fix it.
title: ''
labels: type:bug, qa:pendingVerification
assignees: ''
---

### Describe the bug

<!-- A clear and concise description of what the observation is and the problem it causes. Screenshots and recordings can be used here to aid this description -->

### Expected behaviour

<!-- A description of the behaviour that you would expect -->

### To Reproduce

<!-- Clear steps to reproduce the behavior. This will be later used to review the fix -->

### Environment observed:

- Optimize mode(s):
- Optimize version(s):
- Database(s) (Elasticsearch/OpenSearch):

### Links

<!-- Add links to related SUPPORT/SEC tickets or other issues  -->

### Breakdown

<!-- A breakdown of tasks that need to be completed in order for this to be ready for review. -->
<!--
- [ ] #123
- [ ] Step X
-->

```[tasklist]
### Pull Requests
```

## Bug Lifecycle

### QA Verification

Is this bug reproducible?

- [ ] Yes. If so:
    - [ ] The bug description is clear
    - [ ] The steps to reproduce are clear
    - [ ] The environments observed are correct and complete
    - [ ] The `qa:pendingVerification` label has been removed
    - [ ] The `qa:verified` label has been added
    - [ ] The issue has been moved to "Inbox" status
- [ ] No
    - [ ] The `qa:pendingVerification` label has been removed
    - [ ] The `qa:notVerified` label has been added
    - [ ] The issue has been closed with a sufficient explanation in a comment

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

When all stages are complete, please let the QA know via a comment. If any stages are incomplete,
please reassign it back to the author and move it back to "In Progress"

#### QA Review

- [ ] The change is implemented as described on all target environments/versions/modes
- [ ] The documentation changes are as expected

When all stages are complete, please let the author know via a comment. If any stages are
incomplete, please explain the reasons why in a comment, reassign it back to the author and move it
back to "In Progress"

### Completion

- [ ] All Review stages are successfully completed
- [ ] All associated PRs are merged to the main branch(es) and maintenance branches
- [ ] The correct version labels are applied to the issue
