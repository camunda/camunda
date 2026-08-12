# @camunda/session-heartbeat

Activity-driven session heartbeat for Camunda webapps whose sessions are managed by the
[Camunda security library](https://github.com/camunda/camunda-security-library) (CSL).

The package tracks genuine browser activity — pointer, keyboard, wheel, scroll, and the tab
regaining visibility — and calls CSL's `POST {basePath}/session/heartbeat` endpoint at most once per
interval, only when activity actually happened. It exists so Operate, Tasklist, Optimize, and any
future adopter share one implementation instead of each reimplementing the same listener and
throttle logic.

Background: [CSL ADR-0042 — Configurable session idle timeout driven by client activity](https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0042-configurable-activity-driven-session-idle-timeout.md).

## Why a heartbeat is needed

With `camunda.security.session.heartbeat.enabled=true`, CSL stops treating ordinary backend traffic
as proof of user presence: **only** a call to the heartbeat endpoint extends the session. A host that
enables the flag without a frontend sending heartbeats logs its users out after
`camunda.security.session.max-inactive-interval`, however actively they are using the application.

The flag defaults to `false`, so adopting this package is safe and inert until a host turns it on.

## Installation

```bash
npm install @camunda/session-heartbeat
```

`react` is an optional peer dependency, needed only for the `./react` entry point.

## Usage

### React

```tsx
import {useSessionHeartbeat} from '@camunda/session-heartbeat/react';

function AuthenticatedLayout() {
	useSessionHeartbeat({
		url: '/session/heartbeat',
		csrfToken: () => sessionStorage.getItem('X-CSRF-TOKEN'),
		onUnauthorized: () => {
			authenticationStore.disableSession();
		},
	});

	return <Outlet />;
}
```

Call it once inside the authenticated part of the application, so the heartbeat starts after login
and stops when the user leaves it. Callbacks and `csrfToken` are read fresh on every heartbeat, so
inline functions do not restart the timer; changing `url`, `intervalMs`, or `enabled` does.

### Without React

```ts
import {createSessionHeartbeat} from '@camunda/session-heartbeat';

const stopSessionHeartbeat = createSessionHeartbeat({url: '/session/heartbeat'});

// on teardown
stopSessionHeartbeat();
```

## Options

| Option           | Type                                         | Default     | Description                                                                                                |
| ---------------- | -------------------------------------------- | ----------- | ---------------------------------------------------------------------------------------------------------- |
| `url`            | `string`                                     | —           | The heartbeat endpoint. Must be the current scope's `{basePath}/session/heartbeat`, context path included. |
| `intervalMs`     | `number`                                     | `60000`     | How often activity is checked, and therefore the shortest gap between two heartbeats.                      |
| `csrfToken`      | `string \| null \| undefined \| (() => …)`   | `undefined` | CSRF token, or a getter for it. Sent as `X-CSRF-TOKEN`; omitted when absent or empty.                      |
| `onUnauthorized` | `() => void`                                 | `undefined` | Called when a heartbeat comes back `401` — an expired session, or a missing/stale CSRF token.              |
| `onError`        | `(failure: SessionHeartbeatFailure) => void` | `undefined` | Called for network errors and for any other non-OK response.                                               |
| `enabled`        | `boolean` (`./react` only)                   | `true`      | Set `false` to keep the hook mounted without sending heartbeats.                                           |

### Choosing `intervalMs`

Keep it well under the host's `camunda.security.session.max-inactive-interval` — a heartbeat is what
resets that clock, and one lost request must not cost the user their session. A quarter of the
configured interval or less is a good rule of thumb; the `60s` default suits CSL's `30m` default.

## Behavior worth knowing

- **The endpoint needs a CSRF token.** CSL exempts only `/login` and `/logout` from CSRF protection,
  so a session-bearing `POST /session/heartbeat` without a valid `X-CSRF-TOKEN` is rejected — with
  `401`, not `403`, since the webapp chain maps the CSRF denial onto its auth-failure handler. Pass
  `csrfToken` from wherever the application keeps it.
- **A `401` therefore means "this heartbeat was not accepted", not strictly "the session is gone".**
  A missing or stale token produces the same status as an expired session, so `onUnauthorized` fires
  in both cases. That matches how a webapp's own request layer usually treats `401`; if an
  application needs to tell the two apart, the response body carries a CSRF-specific `detail`.
- **Starting counts as activity**, so the first interval always sends one heartbeat. This surfaces
  broken wiring immediately, at the cost of extending an abandoned session by one interval.
- **At most one heartbeat per interval**, and none at all for an interval with no activity. A
  heartbeat is also skipped while a previous one is still in flight.
- **A `401` does not stop the heartbeat.** The consumer decides what an expired session means;
  unmounting the hook (or setting `enabled: false`) is what stops it.
- **Each tab heartbeats independently.** They share one session, so the cost is one extra request
  per tab per interval; there is no cross-tab coordination.
- **Requests are credentialed** (`credentials: 'include'`) and aborted on teardown.

## Development

```bash
npm run build -w @camunda/session-heartbeat        # bundle + type declarations
npm run typecheck -w @camunda/session-heartbeat
npm run test:unit -w @camunda/session-heartbeat    # Vitest browser mode
```

See [Session heartbeat](../../../../docs/monorepo-docs/frontend/session-heartbeat.md) in the
monorepo docs for the adoption checklist and publishing steps.
