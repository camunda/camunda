# Keycloak for SCIM PoC

> [!NOTE]
> This is only minimal working PoC created during the Identity Team Event '25.
> It is not intended to be used productively!

## How to start

### Download Keycloak SCIM client extension:

```bash
cd providers && wget https://github.com/mitodl/keycloak-scim/releases/download/latest/keycloak-scim-1.0-SNAPSHOT-all.jar
```

### Start Keycloak

```bash
docker compose up -d keycloak
```

### Follow the [instructions for the keycloak extension](https://github.com/mitodl/keycloak-scim?tab=readme-ov-file#setup).

When setting up the User Federation, use:
SCIM 2.0 endpoint: http://localhost:8080/
Auth mode: NONE

### Start Camunda

Currently, it does not matter if Camunda is started with OIDC or basic auth to test the SCIM functionality.

### Add Users in Keycloak

Now, when adding users in Keycloak, they should appear in Camunda logs as being synchronized.
