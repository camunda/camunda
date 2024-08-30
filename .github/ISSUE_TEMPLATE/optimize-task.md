---

name: Optimize Task
about: Describe a task for the technical issue - it may be individual issue or be connected to epic.
title: ""
labels: ["kind/task", "component/optimize"]
assignees: ""

---

### Context

<!-- Please, describe the context and need for this task.-->

### Acceptance Criteria

<!-- Please list the Acceptance Criteria. It will be used during Review -->

### Testing Notes

<!-- Any additional information that can be used for review should be provided here -->

### Links

<!-- Add links to related issues -->

### Breakdown

<!-- A breakdown of tasks that need to be completed in order for this to be ready for review. -->
<!--
- [ ] #123
- [ ] Step X
-->

```[tasklist]
### Pull Requests
```

## Task Lifecycle

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

### Completion

- [ ] All Review stages are successfully completed
- [ ] All associated PRs are merged to the main branch(es) and stable branches
- [ ] The correct version labels are applied to the issue

