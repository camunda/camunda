# Release Process

This document introduces the Monorepo Release and the C8 Release Train ("Release Train"). The Release Train depends on the Monorepo Release. It is written to help Camundi understand these processes and address any questions or doubts.

The Monorepo Release produces the core backend artifacts first. The Release Train then consumes those artifacts and coordinates the downstream component releases, SaaS generation, rollout, and support communication. The train can only depart once the Monorepo artifacts are confirmed as released.

- [Monorepo Release](./release-monorepo.md) — produces artifacts for Zeebe, Operate, Tasklist, Camunda, and Optimize (8.8+)
- [Release Train](./release-train.md) — coordinates rollout and downstream release steps for Identity Management, Connectors, Web Modeler, Console, SaaS generation, and Self-Managed distribution
