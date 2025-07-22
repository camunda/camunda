# Authentication in No-Database Mode

When Camunda is configured to run without secondary storage (`camunda.database.type=none`), authentication capabilities are limited to protect against misleading security flows.

## Supported Authentication Methods

### OIDC Authentication (Limited Functionality)

OIDC authentication is supported in no-database mode but with the following limitations:

- **Groups**: Only groups from OIDC claims are processed (if `camunda.security.authentication.oidc.groups-claim` is configured)
- **Roles**: No role mappings from secondary storage are applied  
- **Tenants**: No tenant mappings from secondary storage are applied
- **Mappings**: No identity mappings from secondary storage are processed
- **Authorized Applications**: No application authorization data from secondary storage

**Configuration Example:**
```yaml
camunda:
  database:
    type: none
  security:
    authentication:
      method: oidc
      oidc:
        groups-claim: groups
        username-claim: preferred_username
        client-id-claim: azp
```

### Basic Authentication (Not Supported)

Basic authentication is **not supported** in no-database mode because it requires access to user credentials stored in secondary storage.

Attempting to configure Basic authentication with `camunda.database.type=none` will result in a startup failure with a clear error message:

```
Basic Authentication is not supported when secondary storage is disabled (camunda.database.type=none). 
Basic Authentication requires access to user data stored in secondary storage. 
Please either enable secondary storage by configuring camunda.database.type to a supported database type, 
or disable authentication by removing camunda.security.authentication.method configuration.
```

## Engine-Only Security

In no-database mode, Camunda relies solely on the engine's internal security checks. This is suitable for:

- Headless deployments
- Engine-only clusters  
- Development environments
- Scenarios where external authentication/authorization systems handle security

## Migration to Full Authentication

To enable full authentication capabilities:

1. Configure a supported database type:
   ```yaml
   camunda:
     database:
       type: postgresql  # or other supported types
   ```

2. Ensure database connectivity and schema setup

3. Configure authentication method as needed:
   ```yaml
   camunda:
     security:
       authentication:
         method: basic  # or oidc
   ```

## Implementation Details

The no-database authentication mode is implemented through:

- `CamundaOAuthPrincipalServiceNoDbImpl`: Provides OIDC principal processing without secondary storage access
- `ConditionalOnSecondaryStorage`: Ensures full authentication components only activate when database is available
- `ConditionalOnNoSecondaryStorage`: Enables no-database specific components
- Fail-fast validation in `WebSecurityConfig` for unsupported configurations

This approach ensures predictable behavior and prevents broken security flows in no-database deployments.