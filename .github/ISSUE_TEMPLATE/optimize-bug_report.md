---

name: Optimize Bug report
about: Report a problem about Optimize and help us fix it.
title: ""
labels: ["kind/bug", "qa/pendingVerification", "component/optimize"]
assignees: ""

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

For managing the issue lifecycle, please use the workflow commands. You can see the available
commands by writing `/help` as a comment on this issue.

### QA Verification

Is this bug reproducible?

- [ ] Yes. If so:
  - [ ] The bug description is clear
  - [ ] The steps to reproduce are clear
  - [ ] The environments observed are correct and complete
- [ ] No

### Review

#### Engineering Review

- [ ] All code targeting the main branch has been reviewed by at least one Engineer
- [ ] The PR targeting the main branch has been approved by the reviewing Engineer
- [ ] If the API has changed, the API documentation is updated
- [ ] All other PRs (docs, controller etc.) in the breakdown have been approved

#### QA Review

- [ ] The change is implemented as described on all target environments/versions/modes
- [ ] The documentation changes are as expected

### Completion

- [ ] All Review stages are successfully completed
- [ ] All associated PRs are merged to the main branch(es) and stable branches
- [ ] The correct version labels are applied to the issue

