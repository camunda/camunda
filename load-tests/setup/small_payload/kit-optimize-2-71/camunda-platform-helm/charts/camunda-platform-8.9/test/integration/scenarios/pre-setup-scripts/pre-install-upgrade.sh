#!/bin/bash
#
# This script will run before the Camunda Helm chart install step in the "upgrade" flow.
# Any necessary tasks should be performed here and removed after the release.
#

## TODO: Remove after the 8.8 release.

# Enable alpha8 chart values syntax for backwards compatibility during the upgrade-patch flow.
sed -i '/# START pre-install-upgrade.sh COMMENT/,/# END pre-install-upgrade.sh COMMENT/  s/ # / /' \
    ${TEST_VALUES_BASE_DIR}/chart-full-setup/values-integration-test-ingress-keycloak.yaml
