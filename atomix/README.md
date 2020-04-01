# Atomix

This module contains code of a hard fork of [Atomix](https://github.com/atomix/atomix).
The fork was adjusted to our needs, which means we reduced it to a level that it only contains code
which we really use and need. For example this contains: a RAFT implementation (with storage),
a SWIM implementation, a usable transport implementation and some glue code.

In the next sub section we will explain why we go the way of doing a hard fork and integrate it into our repository.

## Summary

To be a bit more transparent we want to explain shortly why we did this, why we went this path, what are the pro's and con's.

When we started to use Atomix, we just used it as a normal dependency. Very quick we found some issues which we needed to fix, so we created PR's against the base repository.
In the corresponding base repository was not much activity, which means it took a while to merge the first PR's
We started to merge our PR's in our fork and released our own versions, such that we can make still progress. We saw that during working with atomix and fixing more and more bugs, that our PR's haven't been merged upstream. Also we found out that they moved away from the original code base and rewrote everything in GO.

This was then the point we decided that we can change more and more the code base to our needs. We did that and fixed a lot of bugs in atomix during that time. But we always had the problem that when we fixed or changed something in atomix we needed to release a new version to use it in Zeebe. Sometimes it happens that we just released the newest version of atomix on the day we wanted to released Zeebe, which sometimes broke the build, because we haven't tested it before, with our code base. We switched to using snapshot versions, which improved this a bit. But if we then changed something it could happen that we broke develop and other branches in the Zeebe Repo. It was not easy to develop and test, since if you did a change in atomix you needed to build this locally, build then Zeebe locally and then run tests or a benchmark.

In order to avoid broken builds (develop etc.) and improve the development cycle we decided to merge the Atomix repo into ours.

**Pros:**
 * we have the benefit of one build (everything is build together) - it doesn't break another branch
 * shorter development cycle, we can easily test changes in Atomix in a Zeebe branch
 * easier to create new benchmarks
 * Atomix tests are run more often - which might lead also to new bugs
 * we can easily use our tools and plugins (LGTM, sonarcloud, licensecheck, checkstyle etc.)
 * make release process easier

**Cons:**
 * More flaky tests in the beginning
 * Longer build time

We removed half of their code base, because we don't need it.
We changed the code style to our style, which makes it also much easier to develop.
Furthermore we now have added our copyright to their files, since we changed most of them already.

Please do not touch the copyright headers, we need to keep their copyright on their files.
On new files we create, we will have our License headers with out copyright.
