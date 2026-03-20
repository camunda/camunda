# Joke Generator 3000 -- Manual Test Plan

This test plan validates the Gatekeeper authentication library integration in the Joke Generator 3000 example application. Each test case is designed to be executed by a human tester following the steps exactly as written.

---

## Prerequisites

### Infrastructure Setup

**Start PostgreSQL only (for Basic Auth testing):**

```bash
cd <project-root>/examples/joke-generator-3000
docker compose up -d postgres
```

**Start full infrastructure (for OIDC testing):**

```bash
cd <project-root>/examples/joke-generator-3000
docker compose up -d
# Wait ~30 seconds for Keycloak to start and import the realm
# Verify Keycloak is ready: open http://localhost:8180 in a browser
```

**Tear down infrastructure:**

```bash
docker compose down -v
```

### Application Startup

**Basic Auth profile:**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=basic
```

**OIDC profile:**

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=oidc
```

Application URL: `http://localhost:8080`

### Test Users and Credentials

| Username | Password   | Roles        | Available in          |
|----------|------------|--------------|-----------------------|
| `user`   | `password` | (none)       | Basic auth & Keycloak |
| `admin`  | `password` | `joke-admin` | Basic auth & Keycloak |

**Keycloak admin console:** `http://localhost:8180` (login: `admin` / `admin`)

### Tools Required

- A modern web browser (Chrome, Firefox, or Edge) with Developer Tools
- `curl` or a REST client (e.g., Postman) for API testing
- A terminal for starting/stopping services

---

## Test Suite 1: Basic Auth Profile

Start the app with `--profiles=basic` and PostgreSQL running.

### BASIC-001: Public home page accessible without login

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-001 |
| **Description**  | The landing page at `/` should be accessible without any authentication. |
| **Steps**        | 1. Open a fresh browser (no existing session).<br>2. Navigate to `http://localhost:8080/`. |
| **Expected result** | The home page renders with the app name "Joke Generator 3000", a tagline, and a login button. No redirect to a login page occurs. |
| **Pass/Fail**    | |

### BASIC-002: Protected page redirects to custom login page

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-002 |
| **Description**  | Accessing `/jokes` without authentication should redirect to the custom login page, not Spring's default white-label login. |
| **Steps**        | 1. Open a fresh browser (no existing session).<br>2. Navigate to `http://localhost:8080/jokes`. |
| **Expected result** | The browser redirects to `http://localhost:8080/login`. The login page has custom styling (matching the app's design), a username field, a password field, and a submit button. It is NOT the plain white Spring Security default login form. |
| **Pass/Fail**    | |

### BASIC-003: Login with valid credentials (regular user)

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-003 |
| **Description**  | Logging in as `user` with correct password should succeed and redirect to the jokes page. |
| **Steps**        | 1. Navigate to `http://localhost:8080/login`.<br>2. Enter username: `user`.<br>3. Enter password: `password`.<br>4. Click the login/submit button. |
| **Expected result** | The browser redirects to the jokes page (`/jokes`). The page displays the list of seeded jokes and shows the logged-in username. |
| **Pass/Fail**    | |

### BASIC-004: Login with invalid credentials

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-004 |
| **Description**  | Logging in with incorrect credentials should show an error on the login page. |
| **Steps**        | 1. Navigate to `http://localhost:8080/login`.<br>2. Enter username: `user`.<br>3. Enter password: `wrongpassword`.<br>4. Click the login/submit button. |
| **Expected result** | The browser stays on the login page (or redirects back to `/login?error`). An error message is displayed indicating invalid credentials. The user is NOT logged in. |
| **Pass/Fail**    | |

### BASIC-005: Joke browser accessible after login

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-005 |
| **Description**  | After logging in, the jokes page displays jokes from the database. |
| **Steps**        | 1. Log in as `user` / `password` (per BASIC-003).<br>2. Navigate to `http://localhost:8080/jokes`. |
| **Expected result** | The jokes page renders with a list/table of jokes including the 5 seeded jokes (e.g., "Why do programmers prefer dark mode?"). The logged-in username is visible. |
| **Pass/Fail**    | |

### BASIC-006: Admin page returns 403 for regular user

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-006 |
| **Description**  | A user without the `joke-admin` role should receive a 403 Forbidden when accessing the admin page. |
| **Steps**        | 1. Log in as `user` / `password`.<br>2. Navigate to `http://localhost:8080/jokes/admin`. |
| **Expected result** | The server returns a 403 Forbidden response. A forbidden/error page is shown (not a stack trace). The admin form is NOT displayed. |
| **Pass/Fail**    | |

### BASIC-007: Login as admin user

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-007 |
| **Description**  | Logging in as the admin user should succeed and show admin-specific UI elements. |
| **Steps**        | 1. Log out if currently logged in (or open a fresh browser).<br>2. Navigate to `http://localhost:8080/login`.<br>3. Enter username: `admin`.<br>4. Enter password: `password`.<br>5. Click the login/submit button. |
| **Expected result** | Login succeeds. The jokes page is displayed. An "Admin" link or navigation item is visible (since this user has the `joke-admin` role). |
| **Pass/Fail**    | |

### BASIC-008: Admin page accessible for admin user

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-008 |
| **Description**  | A user with the `joke-admin` role can access the admin page. |
| **Steps**        | 1. Log in as `admin` / `password`.<br>2. Navigate to `http://localhost:8080/jokes/admin`. |
| **Expected result** | The admin page renders with a form containing fields for Setup, Punchline, and Category (dropdown with options: general, programming, science). A submit button is present. |
| **Pass/Fail**    | |

### BASIC-009: Creating a new joke via admin form

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-009 |
| **Description**  | An admin user can create a new joke through the admin form. |
| **Steps**        | 1. Log in as `admin` / `password`.<br>2. Navigate to `http://localhost:8080/jokes/admin`.<br>3. Enter Setup: `Why did the QA engineer cross the road?`<br>4. Enter Punchline: `To test the other side.`<br>5. Select Category: `general`.<br>6. Click the submit button. |
| **Expected result** | The page redirects back to `/jokes/admin` (PRG pattern). The newly created joke appears in the list of jokes on the admin page. Navigating to `/jokes` also shows the new joke. |
| **Pass/Fail**    | |

### BASIC-010: API endpoint `/api/jokes/random` accessible with auth

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-010 |
| **Description**  | The random joke API endpoint returns a joke when authenticated. |
| **Steps**        | 1. Run the following curl command:<br>`curl -u user:password http://localhost:8080/api/jokes/random` |
| **Expected result** | Response status is `200 OK`. The body contains a JSON object with fields: `id`, `setup`, `punchline`, `category`. |
| **Pass/Fail**    | |

### BASIC-011: API endpoint `/api/jokes/random` returns 401 without auth

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-011 |
| **Description**  | The random joke API endpoint rejects unauthenticated requests. |
| **Steps**        | 1. Run the following curl command:<br>`curl -v http://localhost:8080/api/jokes/random` |
| **Expected result** | Response status is `401 Unauthorized`. No joke data is returned. |
| **Pass/Fail**    | |

### BASIC-012: API endpoint `/api/jokes/generate` accessible only with joke-admin role

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-012 |
| **Description**  | The generate joke API endpoint accepts requests from users with the `joke-admin` role. |
| **Steps**        | 1. Run the following curl command:<br>`curl -u admin:password -X POST -H "Content-Type: application/json" -d '{"setup":"Test setup","punchline":"Test punchline","category":"general"}' http://localhost:8080/api/jokes/generate` |
| **Expected result** | Response status is `201 Created`. The body contains a JSON object representing the newly created joke with the submitted fields. |
| **Pass/Fail**    | |

### BASIC-013: API endpoint `/api/jokes/generate` returns 403 for regular user

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-013 |
| **Description**  | The generate joke API endpoint returns 403 for users without the `joke-admin` role. |
| **Steps**        | 1. Run the following curl command:<br>`curl -v -u user:password -X POST -H "Content-Type: application/json" -d '{"setup":"Test","punchline":"Test","category":"general"}' http://localhost:8080/api/jokes/generate` |
| **Expected result** | Response status is `403 Forbidden`. A JSON error body is returned. The joke is NOT created. |
| **Pass/Fail**    | |

### BASIC-014: Logout works and redirects appropriately

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-014 |
| **Description**  | Clicking logout terminates the session and redirects the user. |
| **Steps**        | 1. Log in as `user` / `password`.<br>2. Click the Logout button in the navigation bar. |
| **Expected result** | The user is logged out and redirected to the home page (`/`) or the login page (`/login`). The logout button is no longer visible. |
| **Pass/Fail**    | |

### BASIC-015: After logout, protected pages require re-authentication

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-015 |
| **Description**  | After logging out, accessing a protected page requires logging in again. |
| **Steps**        | 1. Log in as `user` / `password`.<br>2. Log out (per BASIC-014).<br>3. Navigate to `http://localhost:8080/jokes`. |
| **Expected result** | The browser redirects to the login page. The jokes page is NOT displayed without re-authenticating. |
| **Pass/Fail**    | |

### BASIC-016: Static assets load on public and login pages

| Field            | Value |
|------------------|-------|
| **ID**           | BASIC-016 |
| **Description**  | CSS files load correctly on pages that do not require authentication. |
| **Steps**        | 1. Open a fresh browser (no session).<br>2. Navigate to `http://localhost:8080/`.<br>3. Open Developer Tools > Network tab.<br>4. Reload the page.<br>5. Check that `style.css` loaded successfully.<br>6. Navigate to `http://localhost:8080/login`.<br>7. Check that `style.css` loaded successfully on the login page too. |
| **Expected result** | On both the home page and the login page, `style.css` returns HTTP 200 (not 302 redirect or 401). The pages are visually styled (not raw unstyled HTML). |
| **Pass/Fail**    | |

---

## Test Suite 2: OIDC Profile

Stop the app if running. Start the full infrastructure (`docker compose up -d`) and wait for Keycloak to be ready at `http://localhost:8180`. Then start the app with `--profiles=oidc`.

### OIDC-001: Public home page accessible without login

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-001 |
| **Description**  | The landing page at `/` should be accessible without any authentication in OIDC mode. |
| **Steps**        | 1. Open a fresh browser (no existing session).<br>2. Navigate to `http://localhost:8080/`. |
| **Expected result** | The home page renders with the app name and a login button. No redirect to Keycloak occurs. |
| **Pass/Fail**    | |

### OIDC-002: Accessing protected page redirects to Keycloak login

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-002 |
| **Description**  | Accessing a protected page without a session should redirect to Keycloak's login form. |
| **Steps**        | 1. Open a fresh browser (no existing session).<br>2. Navigate to `http://localhost:8080/jokes`. |
| **Expected result** | The browser redirects to `http://localhost:8180/realms/joke-generator/protocol/openid-connect/auth?...` (the Keycloak login page for the `joke-generator` realm). The Keycloak login form is displayed. |
| **Pass/Fail**    | |

### OIDC-003: Login via Keycloak as regular user

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-003 |
| **Description**  | Logging in through Keycloak as a regular user (no roles) should succeed. |
| **Steps**        | 1. Navigate to `http://localhost:8080/jokes` (triggers redirect to Keycloak).<br>2. On the Keycloak login page, enter username: `user`.<br>3. Enter password: `password`.<br>4. Click the Sign In button. |
| **Expected result** | Keycloak authenticates the user and redirects back to the application. |
| **Pass/Fail**    | |

### OIDC-004: Redirect back to app after Keycloak login

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-004 |
| **Description**  | After Keycloak login, the user is redirected back to the originally requested page. |
| **Steps**        | 1. Complete OIDC-003 (login via Keycloak). |
| **Expected result** | The browser lands on `http://localhost:8080/jokes` (or the SSO callback which then redirects to `/jokes`). The jokes page is displayed with the list of jokes and the logged-in username. |
| **Pass/Fail**    | |

### OIDC-005: Joke browser accessible after OIDC login

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-005 |
| **Description**  | After OIDC login, the jokes page renders correctly with data. |
| **Steps**        | 1. Log in as `user` via Keycloak (per OIDC-003).<br>2. Verify the jokes page at `http://localhost:8080/jokes`. |
| **Expected result** | The page shows the seeded jokes. The username `user` is displayed. No "Admin" link is visible (since this user lacks the `joke-admin` role). |
| **Pass/Fail**    | |

### OIDC-006: Admin page returns 403 for regular user

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-006 |
| **Description**  | A Keycloak user without the `joke-admin` realm role receives 403 on the admin page. |
| **Steps**        | 1. Log in as `user` via Keycloak.<br>2. Navigate to `http://localhost:8080/jokes/admin`. |
| **Expected result** | The server returns a 403 Forbidden response. A forbidden/error page is shown. The admin form is NOT displayed. |
| **Pass/Fail**    | |

### OIDC-007: Login as admin user via Keycloak

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-007 |
| **Description**  | Logging in via Keycloak as the admin user (who has the `joke-admin` realm role) should succeed and show admin-specific UI. |
| **Steps**        | 1. Log out if currently logged in (or open a fresh/incognito browser).<br>2. Navigate to `http://localhost:8080/jokes` (triggers Keycloak redirect).<br>3. On the Keycloak login page, enter username: `admin`.<br>4. Enter password: `password`.<br>5. Click Sign In. |
| **Expected result** | Login succeeds. The jokes page displays with an "Admin" link visible in the navigation. |
| **Pass/Fail**    | |

### OIDC-008: Admin page accessible for admin user

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-008 |
| **Description**  | A Keycloak user with the `joke-admin` role can access the admin page. |
| **Steps**        | 1. Log in as `admin` via Keycloak (per OIDC-007).<br>2. Navigate to `http://localhost:8080/jokes/admin`. |
| **Expected result** | The admin page renders with the joke creation form (Setup, Punchline, Category fields and a submit button). |
| **Pass/Fail**    | |

### OIDC-009: Creating a new joke via admin form

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-009 |
| **Description**  | An admin user can create a joke through the admin form in OIDC mode. |
| **Steps**        | 1. Log in as `admin` via Keycloak.<br>2. Navigate to `http://localhost:8080/jokes/admin`.<br>3. Enter Setup: `What do you call a bear with no teeth?`<br>4. Enter Punchline: `A gummy bear.`<br>5. Select Category: `general`.<br>6. Click the submit button. |
| **Expected result** | The page redirects back to `/jokes/admin` (PRG pattern). The new joke appears in the joke list. |
| **Pass/Fail**    | |

### OIDC-010: API endpoint `/api/jokes/random` with Bearer token

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-010 |
| **Description**  | The random joke API endpoint accepts a valid Bearer token from Keycloak. |
| **Steps**        | 1. Obtain an access token from Keycloak:<br>`TOKEN=$(curl -s -X POST 'http://localhost:8180/realms/joke-generator/protocol/openid-connect/token' -d 'grant_type=password&client_id=joke-generator-app&client_secret=joke-generator-secret&username=user&password=password' \| jq -r '.access_token')`<br>2. Call the API:<br>`curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/jokes/random` |
| **Expected result** | Response status is `200 OK`. The body contains a JSON joke object with `id`, `setup`, `punchline`, and `category` fields. |
| **Pass/Fail**    | |

### OIDC-011: API endpoint `/api/jokes/generate` with Bearer token (admin)

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-011 |
| **Description**  | The generate joke API endpoint accepts a Bearer token from a user with the `joke-admin` role. |
| **Steps**        | 1. Obtain an access token for `admin`:<br>`TOKEN=$(curl -s -X POST 'http://localhost:8180/realms/joke-generator/protocol/openid-connect/token' -d 'grant_type=password&client_id=joke-generator-app&client_secret=joke-generator-secret&username=admin&password=password' \| jq -r '.access_token')`<br>2. Call the API:<br>`curl -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"setup":"API joke","punchline":"API punchline","category":"programming"}' http://localhost:8080/api/jokes/generate` |
| **Expected result** | Response status is `201 Created`. The body contains the created joke as JSON. |
| **Pass/Fail**    | |

### OIDC-012: API endpoint `/api/jokes/generate` returns 403 with regular user's token

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-012 |
| **Description**  | The generate joke API endpoint rejects tokens from users without the `joke-admin` role. |
| **Steps**        | 1. Obtain an access token for `user` (no roles):<br>`TOKEN=$(curl -s -X POST 'http://localhost:8180/realms/joke-generator/protocol/openid-connect/token' -d 'grant_type=password&client_id=joke-generator-app&client_secret=joke-generator-secret&username=user&password=password' \| jq -r '.access_token')`<br>2. Call the API:<br>`curl -v -X POST -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"setup":"Test","punchline":"Test","category":"general"}' http://localhost:8080/api/jokes/generate` |
| **Expected result** | Response status is `403 Forbidden`. A JSON error body is returned. The joke is NOT created. |
| **Pass/Fail**    | |

### OIDC-013: Logout redirects to Keycloak logout and back to app

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-013 |
| **Description**  | Clicking logout should end both the app session and the Keycloak session. |
| **Steps**        | 1. Log in as `user` via Keycloak.<br>2. Click the Logout button in the navigation bar.<br>3. Observe the browser URL during the redirect chain. |
| **Expected result** | The browser redirects through Keycloak's logout endpoint (`/realms/joke-generator/protocol/openid-connect/logout`) and then back to the application (home page or login-related page). The user is no longer authenticated. |
| **Pass/Fail**    | |

### OIDC-014: After logout, protected pages redirect to Keycloak again

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-014 |
| **Description**  | After logout, accessing a protected page should trigger a fresh Keycloak login. |
| **Steps**        | 1. Log in as `user` via Keycloak.<br>2. Log out (per OIDC-013).<br>3. Navigate to `http://localhost:8080/jokes`. |
| **Expected result** | The browser redirects to Keycloak's login page. The jokes page is NOT shown without re-authenticating. Keycloak does NOT auto-login (the Keycloak session was also terminated). |
| **Pass/Fail**    | |

### OIDC-015: Token refresh works (session stays alive)

| Field            | Value |
|------------------|-------|
| **ID**           | OIDC-015 |
| **Description**  | The user session remains active across multiple page loads within the token validity period. |
| **Steps**        | 1. Log in as `user` via Keycloak.<br>2. Navigate to `http://localhost:8080/jokes`.<br>3. Wait 30 seconds.<br>4. Reload the page.<br>5. Wait another 30 seconds.<br>6. Navigate to `http://localhost:8080/` and then back to `http://localhost:8080/jokes`. |
| **Expected result** | The user remains logged in across all page loads. No redirect to Keycloak occurs. The session does not expire prematurely. |
| **Pass/Fail**    | |

---

## Test Suite 3: Security Boundary Validation

These tests can be run against either profile. Note which profile you are testing. Run each test in both profiles if time allows.

### SEC-001: Direct access to `/jokes` without auth -- verify redirect

| Field            | Value |
|------------------|-------|
| **ID**           | SEC-001 |
| **Description**  | Directly accessing a protected webapp page without authentication must NOT return 200. |
| **Steps**        | 1. Open a fresh browser (no session).<br>2. Run:<br>`curl -v -o /dev/null -w "%{http_code}" http://localhost:8080/jokes`<br>3. Note the HTTP status code. |
| **Expected result** | The response status is `302 Found` (redirect to login page for basic, or redirect to Keycloak for OIDC). It must NOT be `200 OK`. |
| **Pass/Fail**    | |

### SEC-002: Direct access to `/api/jokes/random` without auth -- verify 401

| Field            | Value |
|------------------|-------|
| **ID**           | SEC-002 |
| **Description**  | API endpoints must return 401 for unauthenticated requests, not redirect. |
| **Steps**        | 1. Run:<br>`curl -v http://localhost:8080/api/jokes/random`<br>2. Note the HTTP status code. |
| **Expected result** | The response status is `401 Unauthorized`. The response does NOT contain a 302 redirect to a login page (API endpoints should not redirect to HTML login forms). |
| **Pass/Fail**    | |

### SEC-003: Manipulated session cookie -- verify rejection

| Field            | Value |
|------------------|-------|
| **ID**           | SEC-003 |
| **Description**  | A forged or tampered session cookie must not grant access to protected resources. |
| **Steps**        | 1. Run:<br>`curl -v -b "JSESSIONID=forged-invalid-session-value-12345" http://localhost:8080/jokes`<br>2. Note the HTTP status code. |
| **Expected result** | The response status is `302 Found` (redirect to login). The forged session cookie is rejected. The jokes page content is NOT returned. |
| **Pass/Fail**    | |

### SEC-004: Access to non-existent paths -- verify catch-all deny returns 404

| Field            | Value |
|------------------|-------|
| **ID**           | SEC-004 |
| **Description**  | Paths not covered by any filter chain should be handled by gatekeeper's catch-all deny chain and return 404. |
| **Steps**        | 1. Run:<br>`curl -v http://localhost:8080/this/does/not/exist`<br>2. Run:<br>`curl -v http://localhost:8080/admin/secret`<br>3. Run:<br>`curl -v http://localhost:8080/.env`<br>4. Note the HTTP status codes. |
| **Expected result** | All three requests return `404 Not Found` (or `401`/`403` depending on gatekeeper's catch-all chain configuration). They must NOT return `200 OK` and must NOT expose any application content. |
| **Pass/Fail**    | |

### SEC-005: Security headers present in responses

| Field            | Value |
|------------------|-------|
| **ID**           | SEC-005 |
| **Description**  | Responses should include standard security headers set by Spring Security. |
| **Steps**        | 1. Run:<br>`curl -v http://localhost:8080/`<br>2. Examine the response headers. |
| **Expected result** | The response includes at least the following headers (Spring Security defaults):<br>- `X-Content-Type-Options: nosniff`<br>- `X-Frame-Options: DENY` (or `SAMEORIGIN`)<br>- `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`<br>Note which headers are present and which are missing. |
| **Pass/Fail**    | |

### SEC-006: Login page is custom styled (not Spring's default white-label)

| Field            | Value |
|------------------|-------|
| **ID**           | SEC-006 |
| **Description**  | The login page must be the custom-designed page, not Spring Security's built-in white-label login. |
| **Steps**        | 1. Start the app in basic auth profile.<br>2. Navigate to `http://localhost:8080/login`.<br>3. Inspect the page visually and via View Source (Ctrl+U). |
| **Expected result** | The page has custom HTML/CSS styling matching the app's design. The HTML source references `/css/style.css`. The page does NOT contain the text "Please sign in" in a plain unstyled form (Spring's default). |
| **Pass/Fail**    | |

---

## Test Suite 4: Cross-cutting

These tests validate UI and UX behaviour across both profiles.

### CROSS-001: Error page renders correctly

| Field            | Value |
|------------------|-------|
| **ID**           | CROSS-001 |
| **Description**  | The error page should render with proper styling when an error occurs. |
| **Steps**        | 1. Log in as `user`.<br>2. Navigate to `http://localhost:8080/jokes/admin` (which should return 403).<br>3. Observe the error/forbidden page rendering. |
| **Expected result** | A styled error page is displayed (not a raw Spring Boot "Whitelabel Error Page" with a stack trace). The page uses the app's CSS and layout. |
| **Pass/Fail**    | |

### CROSS-002: Navigation shows correct links based on role

| Field            | Value |
|------------------|-------|
| **ID**           | CROSS-002 |
| **Description**  | The navigation bar should show role-appropriate links. |
| **Steps**        | 1. Log in as `user` (no roles). Check the navigation bar for links.<br>2. Log out.<br>3. Log in as `admin` (has `joke-admin` role). Check the navigation bar for links. |
| **Expected result** | For `user`: Navigation shows "Jokes" link and "Logout" button. No "Admin" link is visible.<br>For `admin`: Navigation shows "Jokes" link, "Admin" link, and "Logout" button. |
| **Pass/Fail**    | |

### CROSS-003: UI is responsive (narrow viewport)

| Field            | Value |
|------------------|-------|
| **ID**           | CROSS-003 |
| **Description**  | The application should remain usable at narrow viewport widths. |
| **Steps**        | 1. Log in as `admin`.<br>2. Open Developer Tools and toggle Device Toolbar (or resize browser to ~375px width).<br>3. Navigate to `/`, `/jokes`, `/jokes/admin`, and `/login`. |
| **Expected result** | All pages remain readable and usable. No horizontal scrolling is required for main content. Navigation elements are accessible. Forms are usable. |
| **Pass/Fail**    | |

### CROSS-004: "Powered by Gatekeeper" footer visible

| Field            | Value |
|------------------|-------|
| **ID**           | CROSS-004 |
| **Description**  | The layout footer should include the "Powered by Gatekeeper" text on all pages. |
| **Steps**        | 1. Navigate to `http://localhost:8080/` (home page, no login needed).<br>2. Scroll to the bottom of the page.<br>3. Log in and navigate to `/jokes`.<br>4. Scroll to the bottom.<br>5. Navigate to `/jokes/admin` (as admin).<br>6. Scroll to the bottom. |
| **Expected result** | The footer text "Powered by Gatekeeper" (or similar) is visible at the bottom of every page. |
| **Pass/Fail**    | |

---

## Gatekeeper Integration Observations

This section is for the QA engineer to record observations about the developer experience of integrating the Gatekeeper library. Fill in each prompt during or after testing.

### Setup and Configuration

- **How easy was it to start the app with each profile?**
  _[Record observations here: any errors during startup, missing configuration, unclear error messages, etc.]_

- **Was the relationship between `camunda.security.authentication.method` and the required beans/SPIs clear?**
  _[Record observations here]_

- **Did the gatekeeper starter auto-configure correctly without manual bean definitions (other than the SPIs)?**
  _[Record observations here]_

### SPI Contracts

- **Was the `SecurityPathProvider` contract intuitive? Were the method names and return types self-explanatory?**
  _[Record observations here]_

- **Was the `MembershipResolver` contract clear? Was it obvious what to return and in what format?**
  _[Record observations here]_

- **Was the `CamundaUserProvider` contract straightforward? Were there any fields in `CamundaUserInfo` that were confusing or seemed unnecessary for a simple app?**
  _[Record observations here]_

### Security Behaviour

- **Were there any unexpected 403s or 401s during testing? If so, was it easy to debug which filter chain handled the request?**
  _[Record observations here]_

- **Did the catch-all deny chain behave as expected? Were there any paths that were unexpectedly blocked or unexpectedly allowed?**
  _[Record observations here]_

- **Was the distinction between "unprotected paths", "webapp paths", and "API paths" clear and well-documented?**
  _[Record observations here]_

### Error Messages and Debugging

- **When authentication or authorization failed, were the error messages helpful?**
  _[Record observations here]_

- **Were there any startup errors related to gatekeeper? If so, were the error messages actionable?**
  _[Record observations here]_

- **Is there sufficient logging at DEBUG level to trace how gatekeeper processes a request through its filter chains?**
  _[Record observations here]_

### Documentation Gaps

- **Was anything missing from gatekeeper's documentation that would have helped integration?**
  _[Record observations here]_

- **Were there any "gotchas" that should be documented (e.g., path ordering, the need for `/sso-callback` in webapp paths)?**
  _[Record observations here]_

### Overall Assessment

- **Rate the integration difficulty (1 = trivial, 5 = very difficult):** _[1-5]_
- **Would you recommend gatekeeper to another team based on this experience?** _[Yes/No/Maybe, with reasoning]_
- **Top 3 improvement suggestions:**
  1. _[Suggestion]_
  2. _[Suggestion]_
  3. _[Suggestion]_
