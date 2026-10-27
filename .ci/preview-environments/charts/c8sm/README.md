# How to Update the Helm Chart Version

## ⚠️ Important: Check Official Upgrade Guides First!

**ALWAYS consult the official Camunda upgrade documentation before upgrading:**
https://docs.camunda.io/docs/self-managed/deployment/helm/upgrade/

The upgrade guides provide version-specific migration instructions that are essential for successful upgrades.

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

