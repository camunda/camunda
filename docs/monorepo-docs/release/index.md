# Release Process

This document is an introduction to both the Monorepo Release and the C8 Release processes, where the C8 Release (also known as Release Train) requires the Monorepo Release as a step. It is written to help Camundi understand these processes and address any questions or doubts.

The Monorepo release produces the core backend artifacts first. The Release Train then consumes those artifacts and coordinates the downstream component releases, SaaS generation, rollout, and support communication. The train can only depart once the Monorepo artifacts are confirmed as released.

- [Monorepo Release](./release-monorepo.md) — produces artifacts for Zeebe, Operate, Tasklist, Camunda, and Optimize (8.8+)
- [Release Train](./release-train.md) — coordinates rollout and downstream release steps for Identity Management, Connectors, Web Modeler, Console, SaaS generation, and Self-Managed distribution
