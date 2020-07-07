# Release Cycle and Enterprise Support


### Release Cycle

The Zeebe project follows the [semantic version](https://semver.org/) schema,
which defines a version number using the `MAJOR.MINOR.PATCH` pattern.

- `MAJOR` version can make incompatible API changes
- `MINOR` version can add functionality in a backwards compatible manner
- `PATCH` version can make backwards compatible bug fixes.

The Zeebe team strives to release:
- A new minor version of Zeebe every three months
- In between minor versions, two alpha releases (to preview the upcoming minor version)

At the time of writing, Zeebe supports the last two released minor versions with
patch releases. Patch releases are offered on a best effort basis for the
currently supported versions.


### Breaking Changes Before Zeebe 1.0

Given how early we are in the Zeebe journey, we're not ready to make it a 1.0
release, and we want it to be transparent that this was a deliberate decision.
If we do end up having to make breaking API changes, we'd rather do so before
we get to 1.0 and not by moving to 2.0 just a few months after a 1.0 release.

Of course, even in Zeebe's pre-1.0 state, we'll always make our best effort to
avoid breaking changes, to communicate early and often about planned changes,
and if possible, to provide a migration path if we do need to make such a change.


### Supported Environments


#### Zeebe

- **Zeebe Broker/Gateway** the cluster components of Zeebe require OpenJDK 11+
  and optional if the Elasticsearch exporter is used Elasticsearch 6.8.x
- **Zeebe Java Client** the Java client for Zeebe requires OpenJDK 8+
- **Zeebe Go Client** the Go client for Zeebe requires Go 1.13+
- **zbctl** the Zeebe CLI supports latest versions of Windows, MacOS and Linux

#### Camunda Operate

- **Operate Web App/Importer/Archiver** the server components of Camunda
  Operate require OpenJDK 11+ and Elasticsearch 6.8.x
- **Operate Browser App** requires the latest version of Chrome, Firefox or
  Edge on Windows, MacOS and Linux

### Camunda Cloud

Zeebe is built according to [cloud-native
principles](https://github.com/cncf/toc/blob/master/DEFINITION.md), and we want
Zeebe to be the workflow engine for important, emerging use cases running on
modern software architectures.

But even with a best-in-class architecture, operating a distributed workflow
engine 24x7 can be challenging and time consuming. We've heard from a number of
users who would be happy to have us run Zeebe and Operate on their behalf.

With that in mind, we have a dedicated team–additional to the Zeebe core
engineering team building the workflow engine–currently working the first
iteration of Camunda Cloud, where we'll offer Zeebe and Operate as a cloud
service. This will be the first-ever cloud workflow service offered by Camunda,
and we're really excited for what's ahead.

If you'd like to be notified when we open up a beta program for Camunda Cloud,
you can [sign up here](https://camunda.com/products/cloud/).

