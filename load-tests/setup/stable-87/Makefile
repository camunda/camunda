namespace ?= __NAMESPACE__
secondary_storage ?= __STORAGE_TYPE__
enable_optimize ?= __ENABLE_OPTIMIZE__

# Overrides from common defaults:
# - RDBMS, OpenSearch installation, and physical tenants are not supported for this version.
# - Optimize still needs the Elasticsearch overlay even with Elasticsearch secondary storage.
# - The max scenario uses the 8.7 Zeebe extraConfiguration map key.
rdbms_storages =
optimize_self_sufficient_storages =
scenario_max_override_key = zeebe.extraConfiguration.application-override\.yml=
install_storage_target =
physical_tenants_rdbms_values_file =

include ../common.mk
