# Contributing to Operate

Great to see you!

## Table of Contents

* [Issue Tracking](#issue-tracking)
    * [Guidelines](#guidelines)
    * [Working with Issues](#working-with-issues)
* [Definition of Done](#definition-of-done)
* [License header](#license-headers)
* [Commit Message Guidelines](#commit-message-guidelines)


## Issue Tracking

Operate uses Jira to organize the development process. If you want to
report a bug or request a new feature feel free to open a new issue in
our [issue tracker][issues].

### Guidelines

Every issue should have a meaningful name and a description which either describes:
- a new feature with details about the use case the feature would solve or improve
- a problem, how we can reproduce it and what would be the expected behavior
- a change and the intention how this would improve the system

### Working with Issues

If you want to work on an issue please follow the following steps:

1. Check that a [issue][issues] exists for the task you want to work on,
   if not please create one first and have a look at the [issue
   guidelines](#guidelines).
1. Implement the required changes.
1. If you think you finished the issue please check if your implementation
   conforms to our [definition of done](#definition-of-done).
1. Before starting the review process please prepare your changes for reviewing.
   In general the commits should be squashed into meaningful commits with a
   helpful message. This means cleanup/fix etc commits should be squashed into
   the related commit. If you made refactorings or similar which are not
   directly necessary for the task it would be best if they are split up into
   another commit. Rule of thumb is that you should think about how a reviewer
   can best understand your changes. Please follow the [commit message
   guidelines](#commit-message-guidelines).
1. To start the review process please push your changes to the `master` branch.
1. Assign the issue to one developer to review, if you are not sure who
   should review the issue please approach someone in the team.
1. The reviewer will look at your changes in the following days and give
   you either feedback or accept the changes.
    1. If there are changes requested address them in a new commit. Notify the
       reviewer if the issue is ready for review again to start again the review process.
    1. If no changes are requested the reviewer will start the design review by
       assigning a designer to the issue or close the issue.
1. The design reviewer will look at your changes in the following days and give
   you either feedback or accept the changes.
    1. If there are changes requested address them in a new commit. Notify the
       reviewer if the issue is ready for review again to start again the review process.
    1. If no changes are requested the desing reviewer will close the issue or
       assign it to QA or close the issue..
1. The QA engineer will test your changes in the following days and give
   you either feedback or accept the changes.
    1. If there are changes requested address them in a new commit. Notify the
       reviewer if the issue is ready for review to start again the review process.
    1. If no changes are requested the QA engineer will close the issue.


## Definition of Done

Issues may enter the _review_ stage once their implementation conforms to our definition of done:

* All acceptance criteria are met
* signed-off by you from _user perspective_
    * usable
    * discoverable (if feature)
    * fits with regards to existing functionality
    * accessible
* signed-off by you from _maintainer perspective_
    * simple solution
    * understandable
    * sufficiently and well tested (cf. [good unit tests](https://gist.github.com/nikku/1dc2edb553565238c7bf))
    * code is _clean_
    * satisfies our code standards
* signed-off by you from _product design perspective_
    * follows the style guidelines
    * correct colors are used
    * correct font and font size is used
* signed-off by you from _QA engineer perspective__
    * works with "production" build
    * works on supported browsers (latest Chrome, Firefox, Edge)
    * does not break existing functionality
* signed-off by CI
    * continuous integration passes


## License headers

Every source file needs to contain the following license header at it's top:

```
Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
under one or more contributor license agreements. Licensed under a commercial license.
You may not use this file except in compliance with the commercial license.
```

The header can be added manually, it can be set through your IDE's settings so that it is added to new source files automatically.

## Commit Message Guidelines

All commits pushed to the Operate repositories must conform to that convention.

### Purpose

The commit message is what is what describes your contribution.
Its purpose must therefore be to document what a commit contributes to a project.

Its head line __should__ be as meaningful as possible because it is always
seen along with other commit messages.

Its body __should__ provide information to comprehend the commit for people
who care.

Its footer __may__ contain references to external artifacts
(issues it solves, related commits) as well as breaking change notes.


### Format

#### Short form (only subject line)

    <type>(<scope>): <subject>

#### Long form (with body)

    <type>(<scope>): <subject>

    <BLANK LINE>

    <body>

    <BLANK LINE>

    <footer>

First line cannot be longer than __70 characters__, second line is always blank and other lines should be wrapped at __80 characters__! This makes the message easier to read on github as well as in various git tools.

### Subject Line

The subject line contains succinct description of the change.

#### Allowed <type>

 * feat (feature)
 * fix (bug fix)
 * docs (documentation)
 * style (formatting, missing semi colons, …)
 * refactor
 * test (when adding missing tests)
 * chore (maintain)
 * improve (improvement, e.g. enhanced feature)

#### Allowed <scope>

Scope could be anything specifying place of the commit change.

#### <subject> text

 * use imperative, present tense: _change_ not _changed_ nor _changes_ or _changing_
 * do not capitalize first letter
 * do not append dot (.) at the end

### Message Body

 * just as in <subject> use imperative, present tense: _change_ not _changed_ nor _changes_ or _changing_
 * include motivation for the change and contrast it with previous behavior

### Message Footer

#### Referencing issues

Closed bugs / feature requests / issues should be listed on a separate line in the footer prefixed with "Closes" keyword like this:

    Closes OPE-123

or in case of multiple issues:

    Closes OPE-123, OPE-245, OPE-992

### More on good commit Messages

 * http://365git.tumblr.com/post/3308646748/writing-git-commit-messages
 * http://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html

### FAQ for Geeks

##### How to categorize commits which are direct follow ups to merges?
Use `chore(merge): <what>`.

##### I want to commit a micro change. What should I do?
Ask yourself, why it is only a micro change. Use feat = _docs_, _style_ or _chore_ depending on the change of your merge. Please see next question if you consider commiting work in progress.

##### I want to commit work in progress. What should I do?
Do not do it or do it (except for locally) or do it on a non public branch (ie. non master / develop ...) if you need to share the stuff you do.

When you finished your work, [squash](http://gitready.com/advanced/2009/02/10/squashing-commits-with-rebase.html) the changes to commits with reasonable commit messages and push them on a public branch.


[issues]: https://app.camunda.com/jira/secure/RapidBoard.jspa?rapidView=61