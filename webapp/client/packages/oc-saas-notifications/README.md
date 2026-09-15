# @camunda/oc-saas-notifications

SaaS notification bell and panel for Camunda orchestration cluster webapps. It connects the
Camunda composite-components notification runtime to the Camunda design system notification UI.

## Distribution

This package publishes its TypeScript and TSX source without bundling or transpilation. Consumers
must transpile TypeScript dependencies from `node_modules` and provide compatible versions of the
peer dependencies.

The host must also load `@camunda/design-system/styles.css` and render the component inside a
configured `C3UserConfigurationProvider`.

## Usage

```tsx
import {SaasNotifications} from '@camunda/oc-saas-notifications';

<SaasNotifications
	locale="en"
	labels={{
		title: 'Notifications',
		loading: 'Loading notifications...',
		empty: 'You have no notifications.',
		dismissAll: 'Dismiss all',
	}}
/>;
```

The package owns notification fetching, streaming, mutations, analytics, and the bell/panel state
through the composite-components notification provider. The host owns SaaS authentication,
organization and cluster configuration, stage selection, localization, and header placement.

## Development

```bash
npm run typecheck -w @camunda/oc-saas-notifications
npm pack --dry-run -w @camunda/oc-saas-notifications
```

## Publishing

Increment the package version and update consuming workspace versions before merging to `main`.
Then run the `Publish OC SaaS Notifications to npm` workflow, using its dry-run option before
publishing.
