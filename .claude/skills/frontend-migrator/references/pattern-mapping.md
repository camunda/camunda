# Pattern Mapping: Legacy to Orchestration Cluster Webapp

Side-by-side code examples for each pattern transformation. Use this as a reference when migrating specific code — find the legacy pattern you're looking at, then apply the target equivalent.

## Table of contents

1. [Routing](#routing)
2. [Data fetching](#data-fetching)
3. [State management](#state-management)
4. [Styling](#styling)
5. [Component structure](#component-structure)
6. [Testing](#testing)

---

## Routing

### Route definition

**Legacy (React Router):**
```tsx
// operate/client/src/App/index.tsx
import {Paths} from 'modules/Routes';

<Route
  path={Paths.processes()}
  lazy={async () => {
    const {Processes} = await import('./Processes/index');
    return {Component: Processes};
  }}
/>
```

**Target (TanStack Router file-based):**
```tsx
// src/routes/_auth/operate/processes/index.tsx
import {createFileRoute} from '@tanstack/react-router';
import {ProcessesPage} from '#/pages/ProcessesPage';

const Route = createFileRoute('/_auth/operate/processes/')({
  component: ProcessesPage,
});

export {Route};
```

The file path `src/routes/_auth/operate/processes/index.tsx` *is* the URL `/operate/processes`. No path builder object needed.

### Route with data loading

**Legacy:**
```tsx
// Page component fetches its own data
function Dashboard() {
  const {data} = useProcessDefinitionStatistics();
  // ...
}
```

**Target:**
```tsx
// src/routes/_auth/operate/dashboard/index.tsx
import {createFileRoute} from '@tanstack/react-router';
import {queries} from '#/modules/http/queries';
import {DashboardPage} from '#/pages/DashboardPage';

const Route = createFileRoute('/_auth/operate/dashboard/')({
  beforeLoad: async ({context: {queryClient}}) => {
    await queryClient.ensureQueryData(queries.getProcessDefinitionStatistics());
  },
  component: DashboardPage,
  pendingComponent: DashboardSkeleton,
  errorComponent: DashboardError,
  head: () => ({meta: [{title: 'Dashboard | Camunda'}]}),
});

export {Route};
```

### Route params

**Legacy:**
```tsx
// operate/client/src/modules/Routes.tsx
processInstance: (id?: string) => `/processes/${id ?? ':processInstanceId'}`;

// In component:
const {processInstanceId} = useParams<{processInstanceId: string}>();
```

**Target:**
```tsx
// File: src/routes/_auth/operate/processes/$processInstanceId/index.tsx
// The $ prefix makes it a param segment automatically.

const Route = createFileRoute('/_auth/operate/processes/$processInstanceId/')({
  component: ProcessInstancePage,
});

// In component:
const {processInstanceId} = useParams({from: '/_auth/operate/processes/$processInstanceId/'});
```

### Search params with validation

**Legacy:**
```tsx
const [searchParams, setSearchParams] = useSearchParams();
const filter = searchParams.get('filter') ?? 'all';
```

**Target:**
```tsx
// In the route file:
import {z} from 'zod';

const searchSchema = z.object({
  filter: z.enum(['all', 'active', 'incidents']).catch('all'),
  sort: z.enum(['name', 'version']).catch('name'),
});

const Route = createFileRoute('/_auth/operate/processes/')({
  validateSearch: searchSchema,
  component: ProcessesPage,
});

// In the page component:
const {filter, sort} = useSearch({from: '/_auth/operate/processes/'});
// Fully typed, validated, with defaults from .catch()
```

### Redirects

**Legacy:**
```tsx
useEffect(() => {
  if (!isAuthenticated) {
    navigate('/login');
  }
}, [isAuthenticated]);
```

**Target:**
```tsx
// In the route's beforeLoad:
beforeLoad: async ({context: {queryClient}}) => {
  const {response} = await request(endpoints.getCurrentUser());
  if (response === null) {
    throw redirect({to: '/login'});
  }
}
```

---

## Data fetching

### Endpoint definition

**Legacy (scattered across modules):**
```tsx
// operate/client/src/modules/api/v2/variables/searchVariables.ts
const searchVariables = async (payload: QueryVariablesRequestBody) => {
  return requestWithThrow<QueryVariablesResponseBody>({
    url: endpoints.queryVariables.getUrl(),
    method: endpoints.queryVariables.method,
    body: payload,
  });
};
```

**Target (centralized):**
```tsx
// src/modules/http/endpoints.ts — ALL endpoints in one file
import {endpoints as apiEndpoints} from '@camunda/camunda-api-zod-schemas/8.10';

function searchVariables(body: QueryVariablesRequestBody): Request {
  return new Request(getFullURL(apiEndpoints.queryVariables.getUrl()), {
    ...BASE_REQUEST_OPTIONS,
    method: apiEndpoints.queryVariables.method,
    body: JSON.stringify(body),
    headers: {'Content-Type': 'application/json'},
  });
}

export {searchVariables};
```

### Query definition

**Legacy (scattered `use*.query.ts` files):**
```tsx
// operate/client/src/modules/queries/variables/useVariables.ts
function useVariables(filters: Filters) {
  return useInfiniteQuery({
    queryKey: queryKeys.variables.searchWithFilter(filters),
    queryFn: async ({pageParam}) => {
      const {response, error} = await searchVariables({...filters, ...pageParam});
      if (response !== null) return response;
      throw error;
    },
  });
}
```

**Target (centralized `queries.ts`):**
```tsx
// src/modules/http/queries.ts — ALL query options in one file
import {queryOptions} from '@tanstack/react-query';
import * as endpoints from './endpoints';
import {request} from './request';

const queryKeys = {
  variables: {
    searchWithFilter: (filters: Filters) => ['variables', 'search', filters] as const,
  },
} as const;

const queries = {
  searchVariables: (filters: Filters) =>
    queryOptions({
      queryKey: queryKeys.variables.searchWithFilter(filters),
      queryFn: async () => {
        const {response, error} = await request(endpoints.searchVariables(filters));
        if (error !== null) {
          throw error;
        }
        return response.json() as Promise<QueryVariablesResponseBody>;
      },
    }),
};

export {queries, queryKeys};
```

### Using queries in components

**Legacy:**
```tsx
function VariablesList({processInstanceId}: Props) {
  const {data, fetchNextPage, hasNextPage} = useVariables({processInstanceId});
  // ...
}
```

**Target:**
```tsx
function VariablesList({processInstanceId}: Props) {
  const {data} = useSuspenseQuery(queries.searchVariables({processInstanceId}));
  // The route's beforeLoad already prefetched this data,
  // so useSuspenseQuery resolves instantly on first render.
}
```

**`useSuspenseQuery` vs `useQuery`**: `useSuspenseQuery` never returns `isLoading` or `isError` — it suspends for loading (handled by `<Suspense>` or the route's `pendingComponent`) and throws for errors (handled by `errorComponent`). This is the default choice. Reach for `useQuery` only when you need inline loading/error states within a single component.

---

## State management

### MobX store holding server data → TanStack Query

**Legacy:**
```tsx
// MobX store that fetches and caches process instances
class ProcessInstancesStore {
  processInstances: ProcessInstance[] = [];
  isLoading = false;

  constructor() {
    makeObservable(this, {
      processInstances: observable,
      isLoading: observable,
      fetchInstances: action,
    });
  }

  async fetchInstances(filters: Filters) {
    this.isLoading = true;
    const {response} = await requestWithThrow(api.queryProcessInstances(filters));
    if (response !== null) {
      this.processInstances = await response.json();
    }
    this.isLoading = false;
  }
}
```

**Target:** Delete the store entirely. Add to `queries.ts`:
```tsx
const queries = {
  searchProcessInstances: (filters: Filters) =>
    queryOptions({
      queryKey: queryKeys.processInstances.search(filters),
      queryFn: async () => {
        const {response, error} = await request(endpoints.searchProcessInstances(filters));
        if (error !== null) throw error;
        return response.json() as Promise<QueryProcessInstancesResponseBody>;
      },
    }),
};
```

Components consume it with `useSuspenseQuery(queries.searchProcessInstances(filters))`. Loading state is handled by the route's `pendingComponent`. Error state by the route's `errorComponent`.

### MobX store holding filter state → URL search params

**Legacy:**
```tsx
class FiltersStore {
  status = 'all';
  sortBy = 'startDate';

  constructor() {
    makeObservable(this, {
      status: observable,
      sortBy: observable,
      setStatus: action,
      setSortBy: action,
    });
  }

  setStatus(status: string) { this.status = status; }
  setSortBy(sortBy: string) { this.sortBy = sortBy; }
}
```

**Target:** Delete the store. Define search params on the route:
```tsx
// In the route file
const searchSchema = z.object({
  status: z.enum(['all', 'active', 'incidents', 'completed']).catch('all'),
  sortBy: z.enum(['startDate', 'endDate', 'processName']).catch('startDate'),
});

const Route = createFileRoute('/_auth/operate/processes/')({
  validateSearch: searchSchema,
  component: ProcessesPage,
});
```

Components read with `useSearch({ from: '/_auth/operate/processes/' })` and update with `navigate({ search: { status: 'active' } })`.

---

## Styling

### styled-components → SCSS modules

**Legacy (`styled.ts`):**
```tsx
import styled, {css} from 'styled-components';
import {Tile as BaseTile} from '@carbon/react';
import {styles} from '@carbon/elements';

const Container = styled.div`
  padding: var(--cds-spacing-05);
  display: grid;
  gap: var(--cds-spacing-05);
`;

const Tile = styled(BaseTile)`
  ${styles.bodyCompact01};
  border: 1px solid var(--cds-border-subtle-01);
`;

const Title = styled.h2<{$isActive: boolean}>`
  ${styles.productiveHeading02};
  color: ${({$isActive}) =>
    $isActive ? 'var(--cds-text-primary)' : 'var(--cds-text-secondary)'};
`;
```

**Target (`PageName.module.scss` + component):**
```scss
// ProcessesPage.module.scss
@use '@carbon/layout' as layout;
@use '@carbon/type' as type;

.container {
  padding: layout.$spacing-05;
  display: grid;
  gap: layout.$spacing-05;
}

.tile {
  @include type.type-style('body-compact-01');
  border: 1px solid var(--cds-border-subtle-01);
}

.title {
  @include type.type-style('productive-heading-02');
  color: var(--cds-text-primary);

  &[data-inactive] {
    color: var(--cds-text-secondary);
  }
}
```

```tsx
// ProcessesPage.tsx
import styles from './ProcessesPage.module.scss';
import {Tile} from '@carbon/react';

function ProcessesPage() {
  return (
    <div className={styles['container']!}>
      <Tile className={styles['tile']!}>
        <h2
          className={styles['title']!}
          data-inactive={!isActive || undefined}
        >
          {title}
        </h2>
      </Tile>
    </div>
  );
}

export {ProcessesPage};
```

Transient props (`$isActive`) become data attributes or conditional class names. Carbon tokens stay the same — they're CSS custom properties either way.

---

## Component structure

### Exports

**Legacy:**
```tsx
// Some files use default export
export default Dashboard;

// Some use inline named export
export const Dashboard: React.FC = () => { ... };

// Some use named export for React Router lazy loading
export {Dashboard as Component};
```

**Target — always a block export at the end:**
```tsx
function Dashboard() {
  // ...
}

export {Dashboard};
```

### File naming

**Legacy:**
```
App/Dashboard/index.tsx        → import from './Dashboard'
App/Dashboard/styled.ts        → styled-components
App/Dashboard/index.test.tsx   → co-located test
```

**Target:**
```
pages/DashboardPage.tsx             → import from '#/pages/DashboardPage'
pages/DashboardPage.module.scss     → co-located SCSS module
pages/DashboardPage.test.tsx        → co-located test
```

No barrel files, no `index.tsx`, direct file imports with `#/` path aliases.

---

## Testing

### Basic component test

**Legacy (jsdom + Testing Library):**
```tsx
import {render, screen, waitFor} from 'modules/testing-library';
import {MemoryRouter} from 'react-router-dom';
import {QueryClientProvider} from '@tanstack/react-query';
import {getMockQueryClient} from 'modules/react-query/getMockQueryClient';
import {Dashboard} from './index';

const Wrapper = ({children}: {children: React.ReactNode}) => (
  <QueryClientProvider client={getMockQueryClient()}>
    <MemoryRouter>{children}</MemoryRouter>
  </QueryClientProvider>
);

describe('Dashboard', () => {
  it('should render process statistics', async () => {
    mockFetchProcessDefinitionStatistics().withSuccess(mockData);

    render(<Dashboard />, {wrapper: Wrapper});

    expect(await screen.findByText('10 running instances')).toBeInTheDocument();
  });
});
```

**Target (Vitest browser mode):**
```tsx
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockProcessDefinitionStatisticsEndpoint} from '#/shared-test-modules/mock-handlers';
import {describe, expect} from 'vitest';
import {HttpResponse} from 'msw';

describe('<Dashboard />', () => {
  it('should render process statistics', async ({worker}) => {
    worker.use(
      mockProcessDefinitionStatisticsEndpoint({
        successResponse: HttpResponse.json(mockData),
      }),
    );

    const screen = await renderWithRouter('/operate/dashboard');

    await expect
      .element(screen.getByText('10 running instances'))
      .toBeVisible();
  });
});
```

Key differences:
- `it` from `#/vitest-modules/test-extend` (not from `vitest`)
- `worker` fixture provides MSW (not `nodeMockServer`)
- `renderWithRouter('/path')` replaces `MemoryRouter` + `QueryClientProvider` wrapper
- `render()` / `renderWithRouter()` returns `screen` — no global `screen` import
- `await expect.element(...)` replaces `await screen.findByText(...)` + `toBeInTheDocument()`
- No `waitFor` — `expect.element()` retries automatically
- Endpoint mocks from `shared-test-modules/mock-handlers` — not fluent builder mocks
- Never create inline `http.post(...)` / `http.get(...)` handlers in test files — all endpoint mocks must be defined in `shared-test-modules/mock-handlers.ts` using `createEndpointMock()` from `shared-test-modules/mock-endpoint`. If a mock doesn't exist, add it there first.

### Testing absence of elements

**Legacy:**
```tsx
expect(screen.queryByText('Error')).not.toBeInTheDocument();
```

**Target:**
```tsx
await expect.element(screen.getByText('Error')).not.toBeVisible();
```

There is no `queryByText` in Vitest browser mode.

### User interactions

**Legacy:**
```tsx
const {user} = render(<Form />, {wrapper: Wrapper});
await user.click(screen.getByRole('button', {name: /submit/i}));
await user.type(screen.getByLabelText('Name'), 'Alice');
```

**Target:**
```tsx
const screen = await renderWithRouter('/form');
await screen.getByRole('button', {name: /submit/i}).click();
await screen.getByLabelText('Name').fill('Alice');
```

No `user` fixture — interact directly via locators. `.fill()` replaces `.type()`.

### Standalone component test (no routing context)

**Target:**
```tsx
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {StatusBadge} from './StatusBadge';

describe('<StatusBadge />', () => {
  it('should render the active status', async () => {
    const screen = await render(<StatusBadge status="active" />);

    await expect.element(screen.getByText('Active')).toBeVisible();
  });
});
```

Use `render` from `vitest-browser-react` for components that don't need routing. Use `renderWithRouter` for anything that uses `<Link>`, `useNavigate`, or route hooks.
