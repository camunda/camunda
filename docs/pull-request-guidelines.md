# Pull Requests Guidelines

Pull requests help us write better code by providing a place to discuss code changes.
Please help the reviewer as much as possible in understanding your changes.

## Creating a pull request

1. To start the review process create a new pull request on GitHub from your branch to the `main` branch. Give it a meaningful name and describe your changes in the body of the pull request. Lastly add a link to the issue this pull request closes, i.e. by writing in the description `closes #123`. Without referencing the issue, our [changelog generation] will not recognize your PR as a new feature or fix and instead only include it in the list of merged PRs.
2. Assign the pull request to one developer to review, if you are not sure who should review the issue skip this step. Someone will assign a reviewer for you.
3. The reviewer will look at the pull request in the following days and give you either feedback or accept the changes. Your reviewer might use [emoji code](#review-emoji-code) during the reviewing process.
  1. If there are changes requested, address them in a new commit. Notify the reviewer in a comment if the pull request is ready for review again. If the changes are accepted squash them again in the related commit and force push. Then initiate a merge by adding your PR to the merge queue via the `Merge when ready` button.
  2. If no changes are requested, the reviewer will initiate a merge themselves.
4. When a merge is initiated, a bot will merge your branch with the latest
   `main` and run the CI on it.
  1. If everything goes well, the branch is merged and deleted and the issue and pull request are closed.
  2. If there are merge conflicts, the author of the pull request has to manually rebase `main` into the issue branch and retrigger a merge attempt.
  3. If there are CI errors, the author of the pull request has to check if they are caused by its changes and address them. If they are flaky tests, please have a look at this [guide](docs/ci.md#determine-flakiness) on how to handle them. Once the CI errors are resolved, a merge can be retried by simply enqueueing the PR again.

### Preparing for a review

Many of the the points described in this page are taken from an amazing article series written by Michael Lynch.
We highly recommend giving these a read:
- [How to make your code reviewer fall in love with you](https://mtlynch.io/code-review-love/) (~15-minute read)
- [How to Do Code Reviews Like a Human (Part One)](https://mtlynch.io/human-code-reviews-1/) (~18-minute read)
- [How to Do Code Reviews Like a Human (Part Two)](https://mtlynch.io/human-code-reviews-2/) (~15-minute read)

Make sure you've considered the following, before requesting a review.

#### I've reviewed my own code
The best way to help your reviewer in understanding your changes is by standing in their shoes:
> "Imagine reading the code for the first time." - Michael Lynch

It really helps to do this self-review on GitHub in the pull request view, to see exactly what the reviewer would.
This also gives you a fresh look over your changes compared to the comfort of your local IDE.

#### I've written a clear changelist description
The PR description should “summarize any background knowledge the reader needs”. It provides **context** to the commits.

The commit messages should “explain **what** the change achieves, [..] and **why** you’re making this change”.

Consider this separation as different levels of abstraction.
This helps the reviewer understand what changes you've made, why you've made them and how they fit in the bigger picture.
It's also great documentation and stays close to the code in our version control.

> "For a deeper dive into writing excellent changelist descriptions, see [“How to Write a Git Commit Message”](https://chris.beams.io/posts/git-commit/) by Chris Beams and [“My favourite Git commit”](https://dhwthompson.com/2019/my-favourite-git-commit) by David Thompson." - Michael Lynch

#### I've narrowly scoped my changes
> "The smaller and simpler the change, the easier it is for the reviewer to keep all the context in their head" - Michael Lynch

- Split up your changes in small commits
- Split up many commits into multiple PRs
- Inform your reviewer about your usage of small commits, to help them navigate

#### I've separated structural from behavioral changes

> "Whitespace-only changes are easy to review. Two-line changes are easy to review. Two-line functional changes lost in a sea of whitespace changes are tedious and maddening." - Michael Lynch

Separating these different types of changes helps you narrow the scope of your changes.
It also helps the reviewer by more clearly describing your intent for the changes.

Examples:
- move/rename a file and make changes to it
- move a code body and make changes to it
- (1) test behavior, (2) refactor code, (3) change behavior

Try to split commits to help find functional changes:
- `test: verify that foo does x`
- `refactor: move foo to bar`
- `refactor: rename foo to baz`
- `fix: make baz do y`

You can find more details on how to write good commit messages in our [commit message guidelines](docs/commit-message-guidelines.md).

#### Stale pull requests

If there has not been any activity in your PR after a month, it is automatically marked as stale. If it remains inactive, we may decide to close the PR.
When this happens and you're still interested in contributing, please feel free to reopen it.

## Reviewing a pull request

As a reviewer, you are encouraged to use the following [emoji code](#review-emoji-code) in your comments.

The review should result in:
- **Approving** the changes if there are only optional suggestions/minor issues 🔧, throughts 💭, or likes 👍.
  </br > In cases where ❌ suggestions are straightforward to apply from the reviewers perspective, e.g. "a one-liner"-change for which they don't consider another review needed, the reviewer can pre-approve a PR. This unblocks the author to merge right away when they addressed the required changes. In doubt the author can still decide to require another review, or proactively clarify disagreement with the suggestion. The main point here is that pre-approval puts the author back in charge to make a responsible decision on requiring another review or not and if not get the change merged without further delay.
- **Requesting changes** if there are major issues ❌
- **Commenting** if there are open questions ❓

### Review emoji code

The following emojis can be used in a review to express the intention of a comment. For example, to distinguish a required change from an optional suggestion.

- 👍 or `:+1:`: This is great! It always feels good when somebody likes your work. Show them!
- ❓ or `:question:`: I have a question. Please clarify.
- ❌ or `:x:`: This has to change. It’s possibly an error or strongly violates existing conventions.
- 🔧 or `:wrench:`: This is a well-meant suggestion or minor issue. Take it or leave it. Nothing major that blocks merging.
- 💭 or `:thought_balloon:`: I’m just thinking out loud here. Something doesn’t necessarily have to change, but I want to make sure to share my thoughts.

_Inspired by [Microsoft's emoji code](https://devblogs.microsoft.com/appcenter/how-the-visual-studio-mobile-center-team-does-code-review/#introducing-the-emoji-code)._

# Code review guidelines
Code reviews are necessary to end up with a high quality product, but they can be a medium for volatile debates, lead to frustration or even have negative emotional effects on everyone involved.
We want to make code reviews a positive experience.

> "Code reviews are an opportunity to share knowledge and make informed engineering decisions.
> [..]
> a good code reviewer not only finds bugs but provides conscientious feedback to help their teammates improve." - Michael Lynch

Please keep these guidelines in mind, when reviewing someone's changes.

## Minimize lag
Please aim to perform a code review within one working day and try to minimize the time between rounds of review.
The longer you wait to respond to comments, the more time others have to spend to get back into the topic.

## Does the design fit the application?
Ask yourself if the changes fit in the design of the application. Do the changes belong in the application? Are they
integrating well with the existing code?

## Does it do what's intended?
We expect contributors to test their changes to verify it is working correctly. A reviewer should still think about any
edge-cases. The contributor might've missed them and by staying aware during reviews we can prevent obvious bugs before
they are merged.

## Are the changes too complex?
A reviewer is a great judge for the complexity of code changes. The contributor might've been working on it for a while
and knows exactly how the code works. This makes it harder to notice where the code might be too difficult for readers.
A reviewer should be able to quickly understand the changes.

Naming is a part of this complexity. Did the contributor use sensible names that are descriptive enough without being
confusing. Feel free to ask for name changes even if you don't know an alternative yourself.

### Tie notes to principles, not opinions
Explain both your suggested change and the reason for the change.
In the best case, the reason should be based on principles (e.g. Don’t Repeat Yourself, Single Responsibility Principle).
Try to stay objective to have a constructive discussion.

### Aim to bring the code up a letter grade or two
Rather than overwhelming the author with too many comments, focus on scoping the change set into a smaller pull request.
Changes can be postponed to another small pull request.

> "Not perfect, but good enough" - Michael Lynch

## Are the changes tested properly?
Make sure the changes are covered by unit/integration tests that verify all the acceptance criteria of the issue. But don't
just check if there are tests. Also verify that they are sensible tests. Are they testing the correct things? Are the tests
maintainable? Do you see anything that could result in flakiness?

Testing is not limited to just unit and integration tests. For some changes it might be preferred to do a QA run, or start
a benchmark. This is something that needs to be judged based upon the changes. Don't be afraid to ask the contributor for these things.

## Do the changes follow our style-guide?
Our style choices can be found in our [Code-Style](https://github.com/camunda/zeebe/wiki/Code-Style) guide. We try to
automate these rules where we can, but this is not always possible. A reviewer should be aware of this guide and reference
it in reviews when contributors are not following it correctly.
If a style argument is not yet covered pick a solution and raise the topic in the next team meeting.

## Are the changes documented properly?
It's important we provide good documentation to the user. Reviewers should consider this during a review. Are there any
changes in user facing interfaces, or does the way a user interacts with the system change, then these changes should be
documented in [the official documentation](https://github.com/camunda-cloud/camunda-cloud-documentation).

When a new feature is added we should make sure it is documented in the [release announcement](https://drive.google.com/drive/u/0/folders/1DTIeswnEEq-NggJ25rm2BsDjcCQpDape).

Finally, if there are changes in the way we validate BPMN / DMN the Camunda modeling team should be informed to adjust the linting rules.

## Do we log the correct things?
To help our users and ourselves in case of bugs we have to make sure we have proper logging. A reviewer should mention it
to the contributor if the logging is lacking, or if there is excessive logging being done. Please have a look at our
[logging guidelines](https://github.com/camunda/zeebe/wiki/Logging) for more information on logging.

## Are the changes backwards compatible?
A reviewer should check if the changes contain [breaking changes](https://github.com/camunda/zeebe/wiki/Breaking-Changes).
If there are breaking changes the changes should be changed, or a discussion should occur on whether these are acceptable.

## Should the changes be backported?
We support 2 minor versions of Zeebe. If the changes fix a bug they may need to be backported to the previous releases.
A reviewer should remind the contributor to do this when applicable.
