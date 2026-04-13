---
name: c8-e2e-environment
description: Manages the local Docker Compose environment (Elasticsearch + Camunda) for C8 E2E testing. Use when starting, stopping, or verifying the test environment before running Playwright E2E tests.
---

# C8 E2E Environment Skill

Use this skill before running E2E tests to ensure the environment is up and healthy.

## Environment Components

- **Elasticsearch** at `http://localhost:9200`
- **Camunda** (Operate + Tasklist + Identity + Zeebe) at `http://localhost:8080`
- **Docker Compose config:** `qa/c8-orchestration-cluster-e2e-test-suite/config/docker-compose.yml`

## Workflow

### Start

```bash
bash .github/skills/c8-e2e-environment/scripts/start-environment.sh
```

To start in Tasklist V1 mode (legacy):

```bash
bash .github/skills/c8-e2e-environment/scripts/start-environment.sh v1
```

The script starts the containers and waits until Camunda is healthy before returning.

### Stop

```bash
bash .github/skills/c8-e2e-environment/scripts/stop-environment.sh
```

## Required Environment Variables

Create a `.env` file in `qa/c8-orchestration-cluster-e2e-test-suite/` (never commit it):

```env
LOCAL_TEST=true
CORE_APPLICATION_URL=http://localhost:8080
CAMUNDA_AUTH_STRATEGY=BASIC
CAMUNDA_BASIC_AUTH_USERNAME=demo
CAMUNDA_BASIC_AUTH_PASSWORD=demo
ZEEBE_REST_ADDRESS=http://localhost:8080
CAMUNDA_TASKLIST_V2_MODE_ENABLED=true
```

## Health Check

Camunda takes 30–90 seconds to initialize. The start script waits automatically.
If the health check times out, inspect container logs:

```bash
docker logs camunda --tail 100
docker logs elasticsearch --tail 50
```

## Notes

- The `DATABASE=elasticsearch` variable selects the Elasticsearch backend.
- Tasklist V2 is the default mode. Pass `v1` to the start script to disable it.
- The environment must be running before calling `run-tests.sh`.
