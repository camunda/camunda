# Joke Generator 3000 -- Architecture Document

Validation test application for the Gatekeeper authentication library.

## Build constraints

| Constraint          | Value                                                                |
|---------------------|----------------------------------------------------------------------|
| Java                | 21                                                                   |
| Spring Boot         | 4.0.3 (must match gatekeeper to avoid binary incompatibility)        |
| Gatekeeper          | 0.1.0-SNAPSHOT (local Maven repo)                                    |
| Standalone POM      | No parent from gatekeeper or camunda monorepo                        |
| Server-side UI      | Thymeleaf                                                            |
| Database            | PostgreSQL (Flyway migrations)                                       |
| Profiles            | `basic`, `oidc`                                                      |

---

## 1. Project structure

```
joke-generator-3000/
├── ARCHITECTURE.md
├── pom.xml
├── docker-compose.yml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── io/camunda/jokegen/
│   │   │       ├── JokeGeneratorApplication.java
│   │   │       │
│   │   │       ├── config/
│   │   │       │   └── JokeGenSecurityPathProvider.java
│   │   │       │
│   │   │       ├── auth/
│   │   │       │   ├── JokeGenMembershipResolver.java
│   │   │       │   └── JokeGenUserProvider.java
│   │   │       │
│   │   │       ├── controller/
│   │   │       │   ├── HomeController.java
│   │   │       │   ├── JokeBrowserController.java
│   │   │       │   ├── JokeAdminController.java
│   │   │       │   └── JokeApiController.java
│   │   │       │
│   │   │       ├── model/
│   │   │       │   ├── Joke.java              (JPA entity)
│   │   │       │   ├── AppUser.java            (JPA entity, basic auth only)
│   │   │       │   └── UserRole.java           (JPA entity, basic auth only)
│   │   │       │
│   │   │       ├── repository/
│   │   │       │   ├── JokeRepository.java     (Spring Data JPA)
│   │   │       │   ├── AppUserRepository.java
│   │   │       │   └── UserRoleRepository.java
│   │   │       │
│   │   │       └── service/
│   │   │           ├── JokeService.java
│   │   │           └── AppUserDetailsService.java  (UserDetailsService for basic auth)
│   │   │
│   │   ├── resources/
│   │   │   ├── application.yml                (shared config)
│   │   │   ├── application-basic.yml          (basic auth profile)
│   │   │   ├── application-oidc.yml           (OIDC profile)
│   │   │   ├── db/migration/
│   │   │   │   ├── V1__create_jokes_table.sql
│   │   │   │   ├── V2__create_users_tables.sql
│   │   │   │   └── V3__seed_data.sql
│   │   │   └── templates/
│   │   │       ├── layout.html                (shared layout fragment)
│   │   │       ├── home.html                  (landing page)
│   │   │       ├── login.html                 (custom login page, basic auth only)
│   │   │       ├── jokes.html                 (joke browser)
│   │   │       └── admin.html                 (joke admin panel)
│   │   │
│   └── test/
│       └── java/
│           └── io/camunda/jokegen/
│               ├── JokeGeneratorApplicationTest.java
│               ├── config/
│               │   └── JokeGenSecurityPathProviderTest.java
│               ├── controller/
│               │   ├── HomeControllerTest.java
│               │   ├── JokeBrowserControllerTest.java
│               │   ├── JokeAdminControllerTest.java
│               │   └── JokeApiControllerTest.java
│               └── auth/
│                   ├── JokeGenMembershipResolverTest.java
│                   └── JokeGenUserProviderTest.java
└── keycloak/
    └── jokegen-realm.json                     (realm export for Keycloak import)
```

---

## 2. Docker Compose design

File: `docker-compose.yml`

### Services

| Service    | Image                          | Ports       | Notes                                          |
|------------|--------------------------------|-------------|-------------------------------------------------|
| `postgres` | `postgres:16-alpine`           | `5432:5432` | Creates two databases on startup via init script |
| `keycloak` | `quay.io/keycloak/keycloak:26.2` | `8180:8080` | Dev mode, imports realm on first boot            |

### Postgres init

Use a Docker entrypoint init script (`/docker-entrypoint-initdb.d/`) to create both databases:

```sql
-- init-databases.sql (mounted into the container)
CREATE DATABASE jokegen;
CREATE DATABASE keycloak;
```

Environment variables:

```yaml
postgres:
  image: postgres:16-alpine
  environment:
    POSTGRES_USER: postgres
    POSTGRES_PASSWORD: postgres
  ports:
    - "5432:5432"
  volumes:
    - ./init-databases.sql:/docker-entrypoint-initdb.d/init-databases.sql
  healthcheck:
    test: ["CMD-SHELL", "pg_isready -U postgres"]
    interval: 5s
    timeout: 3s
    retries: 5

keycloak:
  image: quay.io/keycloak/keycloak:26.2
  command: start-dev --import-realm
  environment:
    KC_DB: postgres
    KC_DB_URL: jdbc:postgresql://postgres:5432/keycloak
    KC_DB_USERNAME: postgres
    KC_DB_PASSWORD: postgres
    KC_BOOTSTRAP_ADMIN_USERNAME: admin
    KC_BOOTSTRAP_ADMIN_PASSWORD: admin
    KC_HTTP_PORT: 8080
  ports:
    - "8180:8080"
  volumes:
    - ./keycloak/jokegen-realm.json:/opt/keycloak/data/import/jokegen-realm.json
  depends_on:
    postgres:
      condition: service_healthy
```

The application itself runs outside Docker (via `./mvnw spring-boot:run`) to allow live development.

---

## 3. Database schema

All tables live in the `jokegen` database. Managed by Flyway.

### V1__create_jokes_table.sql

```sql
CREATE TABLE jokes (
    id          BIGSERIAL PRIMARY KEY,
    setup       TEXT      NOT NULL,
    punchline   TEXT      NOT NULL,
    category    VARCHAR(50) NOT NULL DEFAULT 'general',
    created_by  VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

### V2__create_users_tables.sql

These tables are used only by the `basic` profile. The OIDC profile uses Keycloak as the user store.

```sql
CREATE TABLE app_users (
    id          BIGSERIAL    PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,    -- bcrypt hash
    display_name VARCHAR(200) NOT NULL,
    email       VARCHAR(200)
);

CREATE TABLE user_roles (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES app_users(id),
    role_name   VARCHAR(100) NOT NULL,
    UNIQUE(user_id, role_name)
);
```

### V3__seed_data.sql

```sql
-- Seed jokes
INSERT INTO jokes (setup, punchline, category, created_by) VALUES
    ('Why do programmers prefer dark mode?', 'Because light attracts bugs.', 'programming', 'system'),
    ('Why did the developer go broke?', 'Because he used up all his cache.', 'programming', 'system'),
    ('What do you call a fake noodle?', 'An impasta.', 'general', 'system'),
    ('Why don''t scientists trust atoms?', 'Because they make up everything.', 'science', 'system'),
    ('What did the ocean say to the beach?', 'Nothing, it just waved.', 'general', 'system');

-- Seed users for basic auth (passwords are bcrypt of "password")
-- $2a$10$... is bcrypt("password")
INSERT INTO app_users (username, password, display_name, email) VALUES
    ('user', '$2a$10$dXJ3SW6G7P50lGmMQoeGUOk5K5KE1pS0r/ABdPQSy8B.hRtFO7XHy', 'Regular User', 'user@example.com'),
    ('admin', '$2a$10$dXJ3SW6G7P50lGmMQoeGUOk5K5KE1pS0r/ABdPQSy8B.hRtFO7XHy', 'Admin User', 'admin@example.com');

INSERT INTO user_roles (user_id, role_name) VALUES
    ((SELECT id FROM app_users WHERE username = 'admin'), 'joke-admin');
```

---

## 4. SPI implementations

### 4.1 SecurityPathProvider -- `JokeGenSecurityPathProvider`

A single `@Component` used by both profiles. It tells gatekeeper which URL patterns belong to which security concern.

```
@Component
public final class JokeGenSecurityPathProvider implements SecurityPathProvider
```

| Method                | Return value                                                                  |
|-----------------------|-------------------------------------------------------------------------------|
| `apiPaths()`          | `Set.of("/api/**")`                                                           |
| `unprotectedApiPaths()` | `Set.of()` (all API paths require authentication)                           |
| `unprotectedPaths()`  | `Set.of("/", "/error", "/css/**", "/js/**", "/images/**", "/actuator/health")` |
| `webappPaths()`       | `Set.of("/jokes/**", "/login/**", "/logout", "/sso-callback")`                |
| `webComponentNames()` | `Set.of("joke-generator")`                                                    |

**Design notes:**

- The landing page `/` is in `unprotectedPaths` so anyone can see it.
- `/login/**` is in `webappPaths` because gatekeeper's basic auth chain uses `formLogin.loginPage("/login")` and needs that path within the webapp chain (it sets `authorizeHttpRequests` to `permitAll()` for the entire webapp chain in basic auth mode, relying on Spring Security's form login to gate access).
- `/sso-callback` is the OIDC redirect URI used by gatekeeper (`REDIRECT_URI` constant).
- Static resources (`/css/**`, `/js/**`, `/images/**`) are unprotected so they load on the public landing page and login page.

### 4.2 MembershipResolver -- `JokeGenMembershipResolver`

```
@Component
public final class JokeGenMembershipResolver implements MembershipResolver
```

**Behaviour by profile:**

| Profile | How it resolves roles                                                                                          |
|---------|----------------------------------------------------------------------------------------------------------------|
| `basic` | Queries `user_roles` table via `UserRoleRepository` for the given `principalId`. Returns a `CamundaAuthentication` with username and role IDs. |
| `oidc`  | Reads roles from the `realm_access.roles` claim in the JWT token claims map. Returns a `CamundaAuthentication` with username and role IDs extracted from claims. |

The resolver is profile-aware. Use `@Profile("basic")` and `@Profile("oidc")` on two separate implementations, or use a single class that injects the active profile and branches. **Recommended approach: two separate `@Component` classes** to keep logic clean:

- `BasicMembershipResolver` (`@Profile("basic")`) -- queries database
- `OidcMembershipResolver` (`@Profile("oidc")`) -- reads JWT claims

Both return:
```java
CamundaAuthentication.of(b -> b
    .user(principalId)
    .roleIds(resolvedRoles));
```

### 4.3 CamundaUserProvider -- `JokeGenUserProvider`

```
@Component
public final class JokeGenUserProvider implements CamundaUserProvider
```

| Method             | Behaviour                                                                                       |
|--------------------|-------------------------------------------------------------------------------------------------|
| `getCurrentUser()` | Gets `CamundaAuthentication` from injected `CamundaAuthenticationProvider`. If null or anonymous, returns null. Otherwise builds a `CamundaUserInfo` with display name, username, email, roles, and `canLogout=true`. For `basic` profile, looks up display name/email from `app_users` table. For `oidc` profile, reads from token claims (`preferred_username`, `email`, `name`). |
| `getUserToken()`   | Returns `null` for basic profile. For OIDC, extracts the access token from Spring Security's `OAuth2AuthenticationToken` in the `SecurityContextHolder`. |

**`CamundaUserInfo` construction:**

```java
new CamundaUserInfo(
    displayName,           // from DB or claims
    username,              // from CamundaAuthentication.authenticatedUsername()
    email,                 // from DB or claims
    List.of("joke-generator"),  // authorized components
    List.of(),             // tenants (not used)
    List.of(),             // groups (not used)
    auth.authenticatedRoleIds(),
    null,                  // salesPlanType (not used)
    Map.of(),              // c8Links (not used)
    true)                  // canLogout
```

---

## 5. Security path mapping

### Filter chain resolution

Gatekeeper builds filter chains from the `SecurityPathProvider` values. Here is how each request path maps:

| URL pattern              | Filter chain         | Access level                                | Notes                                                    |
|--------------------------|----------------------|---------------------------------------------|----------------------------------------------------------|
| `/`                      | Unprotected          | Public                                      | Landing page with branding and login button              |
| `/error`                 | Unprotected          | Public                                      | Spring Boot error page                                   |
| `/css/**`, `/js/**`, `/images/**` | Unprotected | Public                                      | Static assets                                            |
| `/actuator/health`       | Unprotected          | Public                                      | Health check                                             |
| `/api/**`                | Basic/OIDC API chain | Authenticated (any logged-in user)           | REST API; auth via Basic header or Bearer token          |
| `/jokes`                 | Basic/OIDC Webapp chain | Authenticated (any logged-in user)        | Joke browser page                                        |
| `/jokes/admin`           | Basic/OIDC Webapp chain | Role-based (`joke-admin`)                 | Admin panel -- role check done in controller, not by gatekeeper |
| `/login/**`              | Basic/OIDC Webapp chain | Permit all (form login page / OIDC redirect) | Custom login page for basic; OIDC redirects through here |
| `/logout`                | Basic/OIDC Webapp chain | Permit all                                | Logout endpoint                                          |
| `/sso-callback`          | Basic/OIDC Webapp chain | Permit all                                | OIDC redirect callback                                   |
| Everything else          | Catch-all deny       | Denied (404)                                 | Gatekeeper's `protectedUnhandledPathsSecurityFilterChain` |

### Role-based access enforcement

Gatekeeper does not provide built-in role-based path authorization. The `joke-admin` role check must be enforced **in the controller layer**:

1. Inject `CamundaAuthenticationProvider`
2. Call `getCamundaAuthentication().authenticatedRoleIds()`
3. If `"joke-admin"` is not present, return 403

This applies to both:
- `JokeAdminController` (for the `/jokes/admin` page)
- `JokeApiController` (for the `POST /api/jokes/generate` endpoint)

---

## 6. Controller design

### 6.1 HomeController

```
@Controller
public final class HomeController
```

| Method | Endpoint | Auth    | Returns                                              |
|--------|----------|---------|------------------------------------------------------|
| GET    | `/`      | Public  | `home.html` -- branding, app description, login link |

The login link points to `/jokes` (which will trigger authentication redirect if not logged in) for OIDC, or `/login` for basic auth. Determine which link to show using an injected `@Value("${camunda.security.authentication.method}")` property.

### 6.2 JokeBrowserController

```
@Controller
public final class JokeBrowserController
```

| Method | Endpoint | Auth          | Returns                                                                |
|--------|----------|---------------|------------------------------------------------------------------------|
| GET    | `/jokes` | Authenticated | `jokes.html` -- list of jokes from DB, current user info, admin link if user has `joke-admin` role |

Model attributes:
- `jokes` -- `List<Joke>` from `JokeService.getAllJokes()`
- `username` -- from `CamundaAuthenticationProvider`
- `isAdmin` -- boolean, `true` if user has `joke-admin` role
- `userInfo` -- `CamundaUserInfo` from `CamundaUserProvider`

### 6.3 JokeAdminController

```
@Controller
public final class JokeAdminController
```

| Method | Endpoint       | Auth                  | Returns                                                    |
|--------|----------------|-----------------------|------------------------------------------------------------|
| GET    | `/jokes/admin` | `joke-admin` role     | `admin.html` -- form to enter setup/punchline/category     |
| POST   | `/jokes/admin` | `joke-admin` role     | Redirect to `/jokes/admin` after saving (PRG pattern)      |

Both methods must check for `joke-admin` role manually. If the role is missing, return a 403 response (render an error template or throw `AccessDeniedException`).

### 6.4 JokeApiController

```
@RestController
@RequestMapping("/api/jokes")
public final class JokeApiController
```

| Method | Endpoint              | Auth              | Returns                                                         |
|--------|-----------------------|-------------------|-----------------------------------------------------------------|
| GET    | `/api/jokes/random`   | Authenticated     | `200` with JSON: `{"id": 1, "setup": "...", "punchline": "...", "category": "..."}` |
| POST   | `/api/jokes/generate` | `joke-admin` role | `201` with created joke JSON. Request body: `{"setup": "...", "punchline": "...", "category": "..."}` |

The POST endpoint checks `joke-admin` role. If missing, return `403 Forbidden` with a JSON error body.

---

## 7. Profile configuration

### application.yml (shared)

```yaml
spring:
  application:
    name: joke-generator-3000
  datasource:
    url: jdbc:postgresql://localhost:5432/jokegen
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway handles schema
    open-in-view: false
  flyway:
    enabled: true
  thymeleaf:
    cache: false  # dev convenience

server:
  port: 8080

camunda:
  security:
    csrf:
      enabled: false  # simplify for demo app
```

### application-basic.yml

```yaml
camunda:
  security:
    authentication:
      method: BASIC
```

No additional gatekeeper config needed. The `AppUserDetailsService` (implementing Spring Security's `UserDetailsService`) provides user lookup. Gatekeeper's `GatekeeperBasicAuthAutoConfiguration` activates form login with the login page at `/login`.

### application-oidc.yml

```yaml
camunda:
  security:
    authentication:
      method: OIDC
      oidc:
        issuer-uri: http://localhost:8180/realms/joke-generator
        client-id: joke-generator-app
        client-secret: joke-generator-secret
        username-claim: preferred_username
        scope: "openid profile email"
```

---

## 8. Keycloak realm design

File: `keycloak/jokegen-realm.json`

### Realm: `joke-generator`

| Setting             | Value                       |
|---------------------|-----------------------------|
| Realm name          | `joke-generator`            |
| Login theme         | keycloak (default)          |
| Registration        | Disabled                    |

### Client: `joke-generator-app`

| Setting                         | Value                                                       |
|---------------------------------|-------------------------------------------------------------|
| Client ID                       | `joke-generator-app`                                        |
| Client Protocol                 | `openid-connect`                                            |
| Access Type                     | `confidential`                                              |
| Client Secret                   | `joke-generator-secret`                                     |
| Standard Flow Enabled           | `true`                                                      |
| Direct Access Grants            | `true` (for testing with curl)                              |
| Valid Redirect URIs             | `http://localhost:8080/*`                                    |
| Post Logout Redirect URIs       | `http://localhost:8080/*`                                    |
| Web Origins                     | `http://localhost:8080`                                      |
| Full Scope Allowed              | `true`                                                      |

### Realm roles

| Role         | Description                    |
|--------------|--------------------------------|
| `joke-admin` | Can create and manage jokes    |

### Users

| Username | Password   | Email                   | Realm roles  |
|----------|------------|-------------------------|--------------|
| `user`   | `password` | `user@example.com`      | (none)       |
| `admin`  | `password` | `admin@example.com`     | `joke-admin` |

### Token mapper (realm_access)

Keycloak includes `realm_access.roles` in access tokens by default. The `OidcMembershipResolver` reads roles from this claim path:

```java
// In token claims map:
// "realm_access" -> {"roles": ["joke-admin"]}
Map<String, Object> realmAccess = (Map<String, Object>) tokenClaims.get("realm_access");
List<String> roles = (List<String>) realmAccess.get("roles");
```

---

## 9. POM dependencies

The POM uses `spring-boot-starter-parent` as its parent. It does NOT reference any gatekeeper or camunda parent POM.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">

  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>4.0.3</version>
    <relativePath/>
  </parent>

  <groupId>io.camunda.examples</groupId>
  <artifactId>joke-generator-3000</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <name>Joke Generator 3000</name>
  <description>Gatekeeper validation test application</description>

  <properties>
    <java.version>21</java.version>
  </properties>

  <dependencies>
    <!-- Gatekeeper -->
    <dependency>
      <groupId>io.camunda</groupId>
      <artifactId>gatekeeper-spring-boot-starter</artifactId>
      <version>0.1.0-SNAPSHOT</version>
    </dependency>

    <!-- Web & Thymeleaf -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-thymeleaf</artifactId>
    </dependency>

    <!-- Data -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
      <groupId>org.flywaydb</groupId>
      <artifactId>flyway-database-postgresql</artifactId>
      <scope>runtime</scope>
    </dependency>

    <!-- Actuator (health endpoint) -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-actuator</artifactId>
    </dependency>

    <!-- Test -->
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.springframework.security</groupId>
      <artifactId>spring-security-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

**Key notes on dependencies:**

- `spring-boot-starter-security` is NOT listed explicitly because `gatekeeper-spring-boot-starter` transitively pulls it in.
- `spring-boot-starter-oauth2-client` and `spring-boot-starter-oauth2-resource-server` are also transitive through gatekeeper.
- The `spring-boot-starter-parent` version MUST be `4.0.3` to match gatekeeper's compiled-against Spring Boot version. Using a different major version (e.g., 3.4.x) will cause `NoSuchMethodError` or `ClassNotFoundException` at runtime.

---

## 10. How to run

### Basic auth profile

```bash
docker compose up -d postgres        # only need Postgres
./mvnw spring-boot:run -Dspring-boot.run.profiles=basic
```

Then visit `http://localhost:8080`. Log in with `user/password` or `admin/password`.

### OIDC profile

```bash
docker compose up -d                 # Postgres + Keycloak
# Wait for Keycloak to start (~30s)
./mvnw spring-boot:run -Dspring-boot.run.profiles=oidc
```

Then visit `http://localhost:8080`. Click login to be redirected to Keycloak.

---

## 11. AppUserDetailsService (basic auth only)

For the `basic` profile, Spring Security needs a `UserDetailsService` to validate credentials. This is NOT a gatekeeper SPI -- it is standard Spring Security configuration.

```
@Service
@Profile("basic")
public final class AppUserDetailsService implements UserDetailsService
```

- Loads user from `app_users` table by username
- Loads roles from `user_roles` table
- Returns a `User` (Spring Security) with granted authorities mapped from role names
- Passwords are stored as bcrypt hashes

Gatekeeper does not manage user stores. It delegates to Spring Security's `UserDetailsService` for basic auth credential validation.

---

## 12. Thymeleaf templates

All templates extend `layout.html` (Thymeleaf layout dialect or fragment-based composition).

### layout.html
- HTML5 boilerplate with `<head>` (CSS link, title) and `<body>` structure
- Navigation bar: app name "Joke Generator 3000", conditional links (Jokes, Admin -- only if `joke-admin` role), Logout button (if authenticated)
- Footer with "Powered by Gatekeeper"
- Content block replaced by child templates

### home.html
- Hero section with app name and tagline
- "Login" button (links to `/login` for basic, `/jokes` for OIDC)
- Publicly visible, no authentication required

### login.html
- Custom styled login form (`POST /login` with `username` and `password` fields)
- Error message display for failed login attempts
- Only used in `basic` profile (OIDC redirects to Keycloak's login page)
- Must POST to `/login` which gatekeeper's form login intercepts

### jokes.html
- Table/card layout listing all jokes (setup, punchline, category)
- "Get a random joke" button that calls `GET /api/jokes/random` via fetch and displays result
- Shows logged-in username
- If user has `joke-admin` role, show link to admin panel

### admin.html
- Form with fields: Setup (text), Punchline (text), Category (dropdown: general, programming, science)
- Submit calls `POST /jokes/admin` (form post, not API)
- List of recently created jokes below the form
- Only accessible to users with `joke-admin` role

### CSS
- Use a single `style.css` in `src/main/resources/static/css/`
- Clean, minimal design -- no CSS framework required
- Color scheme and layout details are the UI Designer's call

---

## 13. Key design decisions

1. **Role checks in controllers, not in filter chains.** Gatekeeper's filter chains enforce authentication (logged in vs. not logged in). Role-based authorization (`joke-admin`) is the application's responsibility, checked in controller methods. This matches how real Camunda components use gatekeeper.

2. **Two MembershipResolver implementations, not one.** Rather than a single class with conditional branches, use `@Profile` to load the correct implementation. This keeps each implementation focused and testable.

3. **CSRF disabled for simplicity.** The demo app disables CSRF (`camunda.security.csrf.enabled=false`) to avoid the complexity of CSRF token management in Thymeleaf forms and API calls. A production app would enable it.

4. **Single SecurityPathProvider for both profiles.** The path structure is identical regardless of auth method. Gatekeeper selects the correct filter chain (basic vs. OIDC) based on `camunda.security.authentication.method`.

5. **Form login page at `/login` for basic auth.** Gatekeeper hardcodes `LOGIN_URL = "/login"` in `GatekeeperSecurityFilterChainAutoConfiguration`. The custom `login.html` template is served by a simple controller at `GET /login`. The actual form submission (`POST /login`) is handled by Spring Security's form login filter, not by the controller.

6. **No Spring Security `@PreAuthorize` or `@Secured`.** Role checks use gatekeeper's `CamundaAuthenticationProvider` API directly, demonstrating the intended integration pattern.
