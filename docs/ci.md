# Continuous Integration Guide

This is a small guide for things useful around our continuos integration setup.

## Github Action

### Maven repository cache

Within
our [Github action to setup zeebe](/Users/megglos/git/zeebe/.github/actions/setup-zeebe/action.yml)
we make use of [action/cache](https://github.com/actions/cache) to store the content of
the maven repository and share it across jobs and even branches. Primarily to speedup builds and
reduce the
risks of failures due to network hiccups to remote repositories (rare but happened).

#### Delete Maven repository caches

In case you encounter a corrupted cache which you suspect to be the cause of a build failure you may
delete that cache to force a clean rebuild of it.

[Github Actions Cache](https://github.com/actions/gh-actions-cache) provides convenient tooling for
doing so.

You can easily list existing caches with:

```bash
gh actions-cache list

Linux-maven-default-dddf7912807d96783a168b6dda5c5b639c6eb183bfedaab524c6e34f695afba4        586.20 MB  refs/pull/10550/merge     10 minutes ago
```

and delete them via:

```bash
gh actions-cache delete Linux-maven-default-dddf7912807d96783a168b6dda5c5b639c6eb183bfedaab524c6e34f695afba4
```

To determine the key of the cache used you can take a look into the log of the setup-zeebe job.

```
...
Run actions/cache@v3
  with:
    path: ~/.m2/repository
    key: Linux-maven-default-dddf7912807d96783a168b6dda5c5b639c6eb183bfedaab524c6e34f695afba4
    restore-keys: Linux-maven-default-

  env:
    TC_CLOUD_TOKEN: ***
    TC_CLOUD_CONCURRENCY: 4
    ZEEBE_TEST_DOCKER_IMAGE: localhost:5000/camunda/zeebe:current-test
    JAVA_HOME: /opt/hostedtoolcache/Java_Temurin-Hotspot_jdk/17.0.4-101/x64
Received 0 of 614674276 (0.0%), 0.0 MBs/sec
...
```

