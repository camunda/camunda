namespace ?= __NAMESPACE__
secondary_storage ?= __STORAGE_TYPE__
enable_optimize ?= __ENABLE_OPTIMIZE__

# Overrides from common defaults:
# - RDBMS and physical tenants are not supported for this version.
# - The max scenario uses the 8.8 orchestration extraConfiguration map key.
# - 8.8 doesn't have the `/cluster/v2/rebalance` endpoint.
rdbms_storages =
physical_tenants_supported = false
scenario_max_override_key = orchestration.extraConfiguration.application-override\.yml=
cluster_rebalance_supported = false

include ../common.mk
