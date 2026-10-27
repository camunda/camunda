# Session heartbeat

`@camunda/session-heartbeat` tracks genuine browser activity and calls the Camunda security library
(CSL) heartbeat endpoint, so that a webapp session expires from real user inactivity rather than from
the absence of backend traffic.

Design decision:
[CSL ADR-0042 — Configurable session idle timeout driven by client activity](https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0042-configurable-activity-driven-session-idle-timeout.md).

Package source: [`webapp/client/packages/session-heartbeat`](https://github.com/camunda/camunda/tree/main/webapp/client/packages/session-heartbeat).
Its [README](https://github.com/camunda/camunda/blob/main/webapp/client/packages/session-heartbeat/README.md)
is the API reference; this page covers how it fits into the monorepo.

## The backend contract

CSL owns two properties:

| Property                                            | Default | Meaning                                                                        |
| --------------------------------------------------- | ------- | ------------------------------------------------------------------------------ |
| `camunda.security.session.max-inactive-interval`     | `30m`   | Idle timeout. Accepts `30m`, `1800s`, or `PT30M`.                               |
| `camunda.security.session.heartbeat.enabled`          | `false` | Off: any request keeps the session alive. On: only the heartbeat call does.     |

`POST {basePath}/session/heartbeat` is installed on every webapp security chain — the primary surface
and every physical-tenant scope, on both OIDC and Basic auth — and derives from `basePath` exactly
like `/login` and `/logout`. It requires an authenticated session and answers `204 No Content`.

Two consequences shape the frontend:

- **The endpoint exists regardless of the flag.** Sending heartbeats is harmless when
  `heartbeat.enabled=false`, which is what makes adopting the package independent of any host's
  rollout decision.
- **CSRF applies.** CSL exempts only `/login` and `/logout` from CSRF protection, so a heartbeat from
  a session-bearing browser must carry `X-CSRF-TOKEN`. A missing or stale token is rejected with
  `401` (the webapp chain routes the CSRF denial through its auth-failure handler, with the reason in
  the response `detail`), so it reaches the frontend the same way an expired session does.

## Usage in the orchestration cluster webapp

The hook is mounted once in the authenticated route, [`src/routes/_auth/route.tsx`](https://github.com/camunda/camunda/blob/main/webapp/client/apps/orchestration-cluster-webapp/src/routes/_auth/route.tsx),
next to `SessionWatcher`:

```tsx
useSessionHeartbeat({
	url: endpoints.sessionHeartbeatUrl(),
	csrfToken: getCsrfTokenFromStorage,
	onUnauthorized: () => {
		authenticationStore.disableSession();
		reactQueryClient.clear();
	},
});
```

- `endpoints.sessionHeartbeatUrl()` resolves the URL through `getFullURL`, so the configured context
  path is applied like it is for every other call.
- `csrfToken` reuses the token the app's request layer already caches in `sessionStorage`.
- `onUnauthorized` mirrors what `request()` does on a `401`, so a session that expired anyway ends up
  in the same state as one detected by any other call.

The dev server proxies `/session/heartbeat` to `localhost:8080` alongside `/login` and `/logout`, so
heartbeats reach a locally running backend.

Because the heartbeat lives in the `_auth` route, it starts after login and stops on logout or
unmount. Every open tab heartbeats on its own; they share one session, so the cost is one extra
request per tab per interval.

## Adopting it in another frontend

1. Add `@camunda/session-heartbeat` to the app's dependencies.
2. Call `useSessionHeartbeat` once inside the authenticated part of the app — not per page.
3. Pass the scope's own `{basePath}/session/heartbeat`, including the context path.
4. Pass the CSRF token from wherever that app keeps it.
5. Keep `intervalMs` well under the host's `max-inactive-interval` (a quarter or less). The `60s`
   default suits CSL's `30m` default.

A host may only set `camunda.security.session.heartbeat.enabled=true` once **every** frontend served
by that deployment sends heartbeats. Enabling it earlier logs users out mid-session.

## Publishing a new version

Same flow as [Camunda API Zod schemas](./camunda-api-zod-schemas.md):

1. Increment the version in `packages/session-heartbeat/package.json`, update the dependency version
   in consuming workspace packages, run `npm i`, and merge to `main`.
2. Run the [Publish Session Heartbeat to npm](https://github.com/camunda/camunda/actions/workflows/publish-session-heartbeat.yml)
   GitHub Action. Dry-run is enabled by default — uncheck it to publish.
3. Bump the dependency in any consumer outside this workspace.

Behavior changes reach consumers only when they bump the dependency, so a fix to the throttle
interval or the event list is not automatically picked up by apps that pinned an older version.
