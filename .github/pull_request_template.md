## Description

<!-- Please explain the changes you made here. -->

## Related issues

<!-- Which issues are closed by this PR or are related -->

closes #

<!-- Cut-off marker
_All lines under and including the cut-off marker will be removed from the merge commit message_

## Definition of Ready

Please check the items that apply, before requesting a review.

You can find more details in our [wiki page] (https://github.com/camunda/zeebe/wiki/Pull-Requests-and-Code-Reviews).

* [ ] I've reviewed my own code
* [ ] I've written a clear changelist description
* [ ] I've narrowly scoped my changes
* [ ] I've separated structural from behavioural changes
-->

## Definition of Done

<!-- As author please check the items that apply before requesting a review. -->

_Not all items need to be done depending on the issue and the pull request._

Code changes:
* [ ] All acceptance criteria described in the issue are met
* [ ] The changes are backwards compatibility with previous versions
* [ ] If it fixes a bug then PRs are created to backport (https://github.com/korthout/backport-action#how-it-works) the fix to the last two minor versions. You can trigger a backport by assigning labels (e.g. `backport stable/8.2`), or via a comment (`/backport`) to the PR. In case that fails you need to create backports manually.

Testing:
* [ ] There are unit/integration tests that verify all acceptance criterias of the issue
* [ ] New tests are written to ensure backwards compatibility with future versions
* [ ] The behavior is tested manually and the issue contains steps used to test
* [ ] The change has been verified by a QA run
* [ ] The impact of the changes is verified by a benchmark

Documentation:
- [ ] If documentation needs to be updated, an issue is created in the [camunda-docs](https://github.com/camunda/camunda-docs) repo, and the issue is added to our Operate project board.
- [ ] If HELM charts need to be updated, an issue is created in the [camunda-platform-heml](https://github.com/camunda/camunda-platform-helm) repo, and the issue is added to our Operate project board.

Other teams:
If the change impacts another team, an issue has been created for this team, explaining what they need to do to support this change.
- [ ] [Zeebe](https://github.com/camunda/zeebe/issues)
- [ ] [Tasklist](https://github.com/camunda/tasklist/issues)
- [ ] [Optimize](https://github.com/camunda/camunda-optimize/issues)
- [ ] Zeebe Play
      
## Definition of Reviewed

<!-- As a reviewer please check the items that apply before approving this PR -->

- [ ] All acceptance criteria described in the issue are met
- [ ] Unit/integration tests are written, that verify all acceptance criteria of the issue
- [ ] E2E tests are written, if the acceptance criteria can't be covered in unit/integration tests
- [ ] The fix/feature is tested manually by the reviewer

Some additional [review guidelines](https://github.com/camunda/zeebe/wiki/Pull-Requests-and-Code-Reviews#code-review-guidelines).
