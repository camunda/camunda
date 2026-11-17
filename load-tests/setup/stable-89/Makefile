namespace ?= __NAMESPACE__
secondary_storage ?= __STORAGE_TYPE__
enable_optimize ?= __ENABLE_OPTIMIZE__

# Overrides from common defaults:
# - physical tenants are not supported for this version.
# - 8.9 doesn't have the `/cluster/v2/rebalance` endpoint.
physical_tenants_supported = false
cluster_rebalance_supported = false

include ../common.mk
