# How to Update the Helm Chart Version

## ⚠️ Important: Check Official Upgrade Guides First!

**ALWAYS consult the official Camunda upgrade documentation before upgrading:**
https://docs.camunda.io/docs/self-managed/deployment/helm/upgrade/

The upgrade guides provide version-specific migration instructions that are essential for successful upgrades.

## Authentication Modes

The preview environment ingress supports two authentication modes:

### Vouch Proxy (Default)

By default, the ingress uses OAuth2-based authentication via Vouch Proxy. This is suitable for interactive browser-based access.

### LDAP Basic Auth

For automated/programmatic access (e.g., smoke tests, CI pipelines), you can enable LDAP-based Basic Authentication which bypasses Vouch Proxy.

**Configuration:**

```yaml
global:
  preview:
    ingress:
      ldapAuth:
        enabled: true  # Enable LDAP Basic Auth instead of Vouch Proxy
        baseDn: "ou=users,o=camunda,c=com"  # LDAP base DN for authentication
```

**Common base DNs:**
- User accounts: `ou=users,o=camunda,c=com`
- Service/machine accounts: `ou=machine-accounts,ou=applications,o=camunda,c=com`

**Usage with curl:**

```bash
curl -u "username:password" https://your-preview-env.camunda.camunda.cloud/...
```

## Steps

1. **Update Chart.yaml**

   ```bash
   # Edit .ci/preview-environments/charts/c8sm/Chart.yaml
   # Change the camunda-platform dependency version
   ```
2. **Update the lock file**

   ```bash
   cd .ci/preview-environments/charts/c8sm
   helm repo add camunda https://helm.camunda.io
   helm repo update
   helm dependency update
   ```
3. **Check the upstream changelog**
   - Review release notes at https://github.com/camunda/camunda-platform-helm/releases
   - Look for breaking changes and renamed configuration keys
   - Check the upstream `values.yaml` for new defaults
4. **Test locally**

   ```bash
   # Build dependencies
   helm dependency build

   # Test template rendering
   helm template . --name-template test-preview --namespace test-preview

   # Check for errors (should only show config values, not actual errors)
   helm template . --name-template test-preview --namespace test-preview 2>&1 | grep -i "^Error:"
   ```
5. **Common breaking changes to watch for**
   - Component renames (e.g., `core` → `orchestration` in v13.x)
   - Configuration path changes (e.g., `global.security.authentication` → `orchestration.security.authentication`)
   - New required values or deprecated fields
   - Template helper function renames
   - **Important:** If components are renamed, update CI workflows that set image tags!
     - Example: `.github/workflows/preview-env-build-and-deploy.yml` uses `--helm-set camunda-platform.orchestration.image.tag`
6. **Commit all changes**

   ```bash
   git add Chart.yaml Chart.lock values.yaml templates/
   git commit -m "chore: update camunda-platform chart to vX.Y.Z"
   ```

