# GitHub issue guidelines

If you want to report a bug or request a new feature, feel free to open a new issue on [GitHub][issues].

If you report a bug, please help speed up problem diagnosis by providing as much information as possible. Ideally, that would include a small [sample project][sample] that reproduces the problem.

> [!NOTE]
> If you have a general usage question, please ask on the [forum](forum).

Every issue should have a meaningful name and a description that either describes:
- A new feature with details about the use case the feature would solve or
  improve
- A problem, how we can reproduce it, and what the expected behavior would be
- A change and the intention of how this would improve the system

## Starting on an issue

The `main` branch contains the current in-development state of the project. To work on an issue, follow these steps:

1. Check that a [GitHub issue][issues] exists for the task you want to work on.
   If one does not, create one. Refer to the [issue guidelines](#github-issue-guidelines).
2. Check that no one is already working on the issue, and make sure the team would accept a pull request for this topic. Some topics are complex and may touch multiple of [Camunda's Components](https://docs.camunda.io/docs/components/), requiring internal coordination.
3. Checkout the `main` branch and pull the latest changes.

   ```
   git checkout main
   git pull
   ```
4. Create a new branch with the naming scheme `issueId-description`.

   ```
   git checkout -b 123-adding-bpel-support
   ```
5. Follow the [Google Java Format](https://github.com/google/google-java-format#intellij-android-studio-and-other-jetbrains-ides)
   and [Zeebe Code Style](https://github.com/camunda/camunda/wiki/Code-Style) while coding.
6. Implement the required changes on your branch and regularly push your
   changes to the origin so that the CI can run. Code formatting, style, and
   license header are fixed automatically by running Maven. Checkstyle
   violations have to be fixed manually.

   ```
   git commit -am 'feat: add BPEL execution support'
   git push -u origin 123-adding-bpel-support
   ```
7. If you think you finished the issue, please prepare the branch for review. Please consider our [pull requests and code reviews](https://github.com/camunda/camunda/wiki/Pull-Requests-and-Code-Reviews) guide, before requesting a review. In general, the commits should be squashed into meaningful commits with a helpful message. This means cleanup/fix etc. commits should be squashed into the related commit. If you made refactorings it would be best if they are split up into another commit. Think about how a reviewer can best understand your changes. Please follow the [commit message guidelines](#commit-message-guidelines).
8. After finishing up the squashing, force push your changes to your branch.

   ```
   git push --force-with-lease
   ```
