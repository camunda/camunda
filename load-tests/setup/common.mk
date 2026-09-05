# Shared Makefile logic for load-tests/setup/{main,stable-*}.
#
# Defaults represent the latest setup behavior. Older version Makefiles keep only
# values that belong to that version:
# - values baked by newLoadTest.sh, such as namespace, secondary_storage, and
#   enable_optimize
# - overrides for capabilities or Helm values that differ from the latest setup
#
# The including Makefile may override these variables before `include`:
#
# - rdbms_storages
#   Space-separated secondary_storage values backed by an RDBMS. Leave empty for
#   versions without RDBMS support.
#
# - optimize_self_sufficient_storages
#   Space-separated secondary_storage values that already provide their own
#   Elasticsearch/OpenSearch cluster, so Optimize does not need the
#   camunda-platform-values-optimize-elasticsearch.yaml fallback file.
#
# - scenario_max_override_key
#   Helm --set-file key, including the trailing '=', used by the `max` scenario
#   to override platform configuration. This differs by chart version.
#
# - install_storage_target
#   Set to `install-storage` for versions that deploy their own secondary
#   storage database/cluster. Leave empty when the target does not apply.
#
# - physical_tenants_supported
#   Set to `false` for versions without product-side physical-tenant config
#   support, so physical_tenant_count > 0 fails fast instead of silently
#   rendering an incomplete overlay.

rdbms_storages ?= postgresql mysql mariadb mssql oracle
optimize_self_sufficient_storages ?= elasticsearch opensearch
scenario_max_override_key ?= orchestration.extraConfiguration[1].content=
install_storage_target ?= install-storage
physical_tenants_supported ?= true

template_output_dir ?= .
# Enable the chaos-killer CronJob (randomly deletes one matching pod per run).
# Use named targets (make install-chaos) or pass directly: make install chaos=true
chaos ?= false
# Deploy pt1..ptN physical tenants alongside the default one, sharing the same
# secondary storage (rdbms/elasticsearch/opensearch/none) as the default tenant,
# isolated by RDBMS table prefix / ES-OS index prefix (or, for secondary_storage=none,
# by REST-routing/authorization only).
# Pass directly: make install physical_tenant_count=3 secondary_storage=postgresql
physical_tenant_count ?= 0
# Optional: additional Helm configuration for the Camunda Platform release.
# Use this to pass extra `--set`/`-f` flags, for example:
#   make install additional_platform_configuration="--set zeebeGateway.env[0].name=FOO --set zeebeGateway.env[0].value=bar"
# See the load test README for more examples and details.
additional_platform_configuration ?=
# Optional: additional Helm configuration for the load test release.
# Use this to pass extra `--set`/`-f` flags when installing/upgrading the load tests.
# See the load test README for example values and guidance.
additional_load_test_configuration ?=

helm_chart_platform = charts/camunda-platform

# Scenario: controls the workload profile for the load test.
# Options: latency, realistic, typical, max, archiver
# Use named targets (make max) or pass directly: make install scenario=max
scenario ?=

ifeq ($(scenario),latency)
_scenario_load_test_flags = --set load-tester.starter.rate=1 --set load-tester.workers.worker.replicas=1
_scenario_platform_flags =
else ifeq ($(scenario),realistic)
_scenario_load_test_flags = -f load-tester-values-realistic-benchmark.yaml
_scenario_platform_flags =
else ifeq ($(scenario),typical)
_scenario_load_test_flags = --set load-tester.starter.rate=50 --set load-tester.workers.worker.replicas=6 --set load-tester.starter.bpmnXmlPath=bpmn/typical_process.bpmn
_scenario_platform_flags =
else ifeq ($(scenario),max)
ifeq ($(scenario_max_override_key),)
$(error scenario=max requires scenario_max_override_key to be declared by the version Makefile)
endif
_scenario_load_test_flags = --set load-tester.starter.rate=300
_scenario_platform_flags = --set-file '$(scenario_max_override_key)./camunda-platform-override-values.yaml'
else ifeq ($(scenario),archiver)
_scenario_load_test_flags = --set load-tester.starter.rate=1 --set load-tester.starter.rateDuration=10m --set load-tester.starter.processId=multiInstanceElements --set load-tester.starter.bpmnXmlPath=bpmn/multiInstanceElements.bpmn --set load-tester.starter.payloadPath=bpmn/multiInstanceElementsPayload.json --set load-tester.workers.worker.replicas=0
_scenario_platform_flags =
else
_scenario_load_test_flags =
_scenario_platform_flags =
endif

# Construct platform values files based on configuration
# Starting with the defaults, which are always applied and set common baseline configuration for all
# set ups. The other values files are focused and only contain the necessary overrides for each storage type.
platform_values = -f camunda-platform-values-defaults.yaml

# The configuration for the "load test setup" local Helm Chart
helm_chart_load_test_setup = charts/load-test-setup
load_test_setup_values = -f load-test-setup-values.yaml
additional_load_test_setup_configuration ?=
# Makefile-side load-test-setup flags. Separate from `additional_load_test_setup_configuration`,
# which CI sets on the make command line and would suppress `+=` here.
_load_test_setup_flags =

# The Docker image tag for the load test metrics exporter
metrics_exporter_image_tag = latest

# physical_tenant_count > 0 requires product-side physical-tenant config support.
# Fail fast on versions that don't have it instead of silently rendering an incomplete overlay.
# (Nested conditionals are left-aligned: a leading tab would make `$(error ...)` look like a recipe.)
ifneq ($(physical_tenant_count),0)
ifneq ($(physical_tenants_supported),true)
$(error physical_tenant_count > 0 is not supported for this Camunda version)
endif
endif

# Add secondary storage values
ifneq ($(filter $(secondary_storage),$(rdbms_storages)),)
	platform_values += -f camunda-platform-values-rdbms.yaml
else ifeq ($(secondary_storage),none)
	_load_test_setup_flags += --set global.extraConfig.load-tester.monitor-data-availability=false
endif
platform_values += -f camunda-platform-values-$(secondary_storage).yaml

# Layer the pt1..ptN physical-tenant overlay on top, generated at install/template time
# (not scaffold time) so re-running `make install` with a different physical_tenant_count
# on an existing namespace doesn't require re-scaffolding via newLoadTest.sh. See the
# generate-physical-tenant-values target below and generate-physical-tenant-values.sh.
ifneq ($(physical_tenant_count),0)
platform_values += -f camunda-platform-physical-tenants.yaml
endif

# Disable Optimize if not enabled
ifneq ($(enable_optimize),true)
	platform_values += --set optimize.enabled=false
else
	platform_values += -f camunda-platform-values-optimize.yaml

    ifeq ($(secondary_storage),opensearch)
        # When deploying the OpenSearch secondary storage, Optimize needs the
        # OpenSearch-specific exporter/client configuration.
        ifneq ($(wildcard camunda-platform-values-optimize-opensearch.yaml),)
            platform_values += -f camunda-platform-values-optimize-opensearch.yaml
        endif

    else ifeq ($(secondary_storage),elasticsearch)
        # When deploying the Elasticsearch secondary storage, Optimize needs the
        # Elasticsearch-specific exporter/client configuration.
        platform_values += -f camunda-platform-values-optimize-elasticsearch.yaml
    else
        ifeq ($(filter $(secondary_storage),$(optimize_self_sufficient_storages)),)
            # If we are not using Elasticsearch/OpenSearch as a secondary storage, Optimize will
            # be configured with the Elasticsearch backend, either:
            # * Using the same Elasticsearch cluster as the secondary storage, if
            #   the secondary storage is also Elasticsearch.
            # * Or using its dedicated Elasticsearch cluster, different from the
            #   configured secondary storage.
            platform_values += -f camunda-platform-values-optimize-elasticsearch.yaml
        endif
    endif
endif

# Platform values with stable configuration
platform_values_stable = $(platform_values) -f values-stable.yaml

ifeq ($(chaos),true)
	_load_test_setup_flags += --set chaosKiller.enabled=true
endif

# Shared helm flags for the load-test-setup chart, used by both install and template targets.
load_test_setup_flags = $(load_test_setup_values) \
    -f load-tester-values-defaults.yaml \
    $(_load_test_setup_flags) \
    $(_scenario_load_test_flags) \
    $(additional_load_test_setup_configuration) \
    $(additional_load_test_configuration)

.PHONY: all
all: install

.PHONY: install
install: check-deadline install-load-test-setup $(install_storage_target) install-platform

.PHONY: install-stable
install-stable: check-deadline install-load-test-setup $(install_storage_target) install-platform-stable

# When physical_tenant_count > 0, also deploy pt1..ptN's own load testers. Appended as the
# last prerequisite so it runs after the platform is up and the load-test-credentials secret
# exists. Only versions with physical_tenants_supported=true define
# install-load-test-physical-tenants; harmless no-op declaration everywhere else since the
# fail-fast gate above already rejects physical_tenant_count > 0 there.
ifneq ($(physical_tenant_count),0)
install: install-load-test-physical-tenants
install-stable: install-load-test-physical-tenants
install-platform: generate-physical-tenant-values
install-platform-stable: generate-physical-tenant-values
template: generate-physical-tenant-values
template-stable: generate-physical-tenant-values
endif

# Fail fast if the namespace TTL deadline (read from load-test-setup-values.yaml,
# the single source of truth) is today or in the past — the TTL cleanup
# workflow will delete the namespace and undo the install. To extend, edit
# `deadlineDate` in load-test-setup-values.yaml and `make install-load-test-setup`.
.PHONY: check-deadline
check-deadline:
	@deadline_date=$$(awk -F'"' '/^[[:space:]]*deadlineDate:/ {print $$2; exit}' load-test-setup-values.yaml); \
	if [ -z "$$deadline_date" ]; then \
		echo "ERROR: could not parse deadlineDate from load-test-setup-values.yaml."; \
		exit 1; \
	fi; \
	today=$$(date +%Y-%m-%d); \
	if [ "$$today" \> "$$deadline_date" ] || [ "$$today" = "$$deadline_date" ]; then \
		echo "ERROR: namespace deadline date ($$deadline_date) is today or earlier (today: $$today)."; \
		echo "       The TTL cleanup workflow will delete this namespace."; \
		echo "       To extend, edit deadlineDate in load-test-setup-values.yaml and run:"; \
		echo "         make install-load-test-setup"; \
		exit 1; \
	fi

# The namespace **must** exist before trying to install/upgrade with Helm:
# normally, Helm should be able to create it itself, but our camunda-benchmark
# Teleport/RBAC configuration returns "permission denied" when trying to list
# secrets on a non-existent namespace (instead of "not found"), which stop Helm
# prematurely with an error instead of proceeding with the creation of a new
# namespace.
.PHONY: create-namespace
create-namespace:
	@echo "Making sure the namespace $(namespace) exists..."
	@# Sleep a bit to let Kubernetes finish the creation of the namespace and make it fully consistent for read and write operations.
	kubectl get namespace "$(namespace)" > /dev/null 2>&1 || (kubectl create namespace "$(namespace)" && sleep 1)

ifneq ($(install_storage_target),)
# Install secondary storage based on configuration
.PHONY: install-storage
install-storage:
ifeq ($(secondary_storage),mysql)
	@echo "Installing MySQL database for namespace $(namespace)..."
	# Install MySQL database - configuration provided via camunda-platform-values-defaults.yaml, camunda-platform-values-rdbms.yaml and camunda-platform-values-mysql.yaml
	helm upgrade --install mysql oci://registry-1.docker.io/bitnamicharts/mysql \
		--namespace $(namespace) \
		$(platform_values)
else ifeq ($(secondary_storage),mariadb)
	@echo "Installing MariaDB database for namespace $(namespace)..."
	# Install MariaDB database - configuration provided via camunda-platform-values-defaults.yaml, camunda-platform-values-rdbms.yaml and camunda-platform-values-mariadb.yaml
	helm upgrade --install mariadb oci://registry-1.docker.io/bitnamicharts/mariadb \
		--namespace $(namespace) \
		$(platform_values)
else ifeq ($(secondary_storage),mssql)
	@echo "Installing MSSQL database for namespace $(namespace)..."
	# Deploy MSSQL via plain Kubernetes manifests (mssql.yaml) since no good public chart is available
	kubectl apply --namespace $(namespace) -f databases/mssql.yaml
else ifeq ($(secondary_storage),oracle)
	@echo "Installing Oracle database for namespace $(namespace)..."
	# Deploy Oracle Free 23c via plain Kubernetes manifests (oracle.yaml) since no maintained public chart is available
	kubectl apply --namespace $(namespace) -f databases/oracle.yaml
endif
endif

ifeq ($(physical_tenants_supported),true)
# Generates camunda-platform-physical-tenants.yaml (see generate-physical-tenant-values.sh)
# when physical_tenant_count > 0. A no-op (the script removes any stale file) otherwise, so
# this can be an unconditional prerequisite of install-platform(-stable)/template(-stable).
.PHONY: generate-physical-tenant-values
generate-physical-tenant-values:
	../generate-physical-tenant-values.sh "$(secondary_storage)" "$(physical_tenant_count)" "$(rdbms_storages)"

# Deploy pt1..ptN's own load testers, sharing the default tenant's secondary storage and its
# load-test-credentials secret. The camunda-load-tests subchart hardcodes the starter/worker
# resource names, so a second Helm release per tenant would collide. Instead we render only
# those two templates from the same chart, values, scenario and image as the default tester,
# rename them to *-pt<i>, and apply — looped over pt1..ptN. Each tenant gets its own
# CAMUNDA_CLIENT_PHYSICAL_TENANT_ID env var: the load-tester's camunda-spring-boot-starter client
# turns that into both the `Camunda-Physical-Tenant` gRPC metadata header (so its job stream
# registers into the tenant's own partition group instead of leaking into "default") and, via
# prefixPhysicalTenantPath (default true), the `/physical-tenants/<tenant>` REST path prefix —
# so both gRPC and REST route correctly without a per-tenant secret or address override.
#
# The extraEnvVars index below (4) is appended after the 4 entries already set in
# global.extraEnvVars by scenarios/load-tester-values-defaults.yaml — currently
# LOAD_TESTER_LOG_APPENDER (0), LOAD_TESTER_LOG_STACKDRIVER_SERVICENAME (1),
# LOAD_TESTER_LOG_STACKDRIVER_SERVICEVERSION (2), OPTIMIZE_LOADTEST_CLIENT_SECRET (3).
# Helm merges --set on a list index positionally, not by appending, so reusing an
# already-used index (0-3) would silently overwrite one of those defaults instead of
# adding a new entry. Bump this index if that file's global.extraEnvVars list grows.
.PHONY: install-load-test-physical-tenants
install-load-test-physical-tenants:
	@for i in $$(seq 1 $(physical_tenant_count)); do \
	  tenant="pt$$i"; \
	  echo "Deploying the $$tenant physical-tenant load tester for namespace $(namespace)..."; \
	  helm template load-test-setup $(helm_chart_load_test_setup) \
	      --namespace $(namespace) \
	      -s charts/load-tester/templates/starter.yaml \
	      -s charts/load-tester/templates/workers.yaml \
	      $(load_test_setup_flags) \
	      --set load-tester.enabled=true \
	      --set global.preferRest.enabled=true \
	      --set global.extraEnvVars[4].name=CAMUNDA_CLIENT_PHYSICAL_TENANT_ID \
	      --set global.extraEnvVars[4].value=$$tenant \
	    | sed -E "s/: starter$$/: starter-$$tenant/; s/: worker$$/: worker-$$tenant/" \
	    | kubectl apply -n $(namespace) -f - ; \
	done
endif

# Install/upgrade Camunda Platform helm chart
.PHONY: install-platform
install-platform:
	helm upgrade $(namespace) $(helm_chart_platform) \
		--install \
		--force-conflicts \
		--namespace $(namespace) \
		--reset-then-reuse-values \
		--render-subchart-notes \
		$(platform_values) \
		$(_scenario_platform_flags) \
		$(additional_platform_configuration)

# Install/upgrade Camunda Platform on stable VMs
.PHONY: install-platform-stable
install-platform-stable:
	helm upgrade $(namespace) $(helm_chart_platform) \
		--install \
		--force-conflicts \
		--namespace $(namespace) \
		--reset-then-reuse-values \
		--render-subchart-notes \
		$(platform_values_stable) \
		$(_scenario_platform_flags) \
		$(additional_platform_configuration)

# Install the load-test-setup Helm Chart (includes the camunda-load-tests subchart).
# Load-test scenario flags and values are passed here; no separate install-load-test target needed.
.PHONY: install-load-test-setup
install-load-test-setup: create-namespace
	helm upgrade load-test-setup $(helm_chart_load_test_setup) \
		--install \
		--force-conflicts \
		--namespace $(namespace) \
		--reset-then-reuse-values \
		--render-subchart-notes \
		--take-ownership --create-namespace \
		$(load_test_setup_flags)

# Generates templates from the Camunda Platform helm chart
.PHONY: template
template:
	helm template $(namespace) $(helm_chart_platform) \
		--namespace $(namespace) \
		$(platform_values) \
		$(_scenario_platform_flags) \
		$(additional_platform_configuration) \
		--output-dir $(template_output_dir)

.PHONY: template-stable
template-stable:
	helm template $(namespace) $(helm_chart_platform) \
		--namespace $(namespace) \
		$(platform_values_stable) \
		$(_scenario_platform_flags) \
		$(additional_platform_configuration) \
		--output-dir $(template_output_dir)

.PHONY: template-load-test-setup
template-load-test-setup:
	helm template load-test-setup $(helm_chart_load_test_setup) \
		--namespace $(namespace) \
		$(load_test_setup_flags) \
		--output-dir $(template_output_dir)

# Renders the load-test-setup chart with the chaos-killer enabled.
.PHONY: template-load-test-setup-chaos
template-load-test-setup-chaos:
	$(MAKE) template-load-test-setup chaos=true

# Print the resolved scenario flags without running any Helm commands
.PHONY: print-scenario
print-scenario:
	@echo "Scenario:         $(if $(scenario),$(scenario),(none — chart defaults apply))"
	@echo "Load test flags:  $(if $(_scenario_load_test_flags),$(_scenario_load_test_flags),(none))"
	@echo "Platform flags:   $(if $(_scenario_platform_flags),$(_scenario_platform_flags),(none))"

# Chaos shortcuts — install (or render) with the chaos-killer CronJob enabled.
.PHONY: install-chaos install-stable-chaos
install-chaos:
	$(MAKE) install chaos=true
install-stable-chaos:
	$(MAKE) install-stable chaos=true

# Workload scenario shortcuts — each runs 'make install' with the corresponding scenario profile.
# For stable VMs, use: make install-stable scenario=<name>
.PHONY: latency realistic typical max archiver
latency:
	$(MAKE) install scenario=latency
realistic:
	$(MAKE) install scenario=realistic
typical:
	$(MAKE) install scenario=typical
max:
	$(MAKE) install scenario=max
archiver:
	$(MAKE) install scenario=archiver

.PHONY: clean
clean:
	@# Explicitly delete the Keycloak resources from the keycloak-operator namespace.
	@# We could also uninstall the load-test-setup Helm Chart, but it returns a
	@# (completely safe) error (about being "forbidden" while deleting Secrets)
	@# due to how our Kubernetes RBACs are configured, and this error is likely
	@# going to raise more questions.
	@echo "Deleting Keycloak-related resources from the keycloak-operator namespace..."
	-kubectl delete keycloak,secret --namespace keycloak-operator --selector "camunda.io/load-test-namespace=$(namespace)" --ignore-not-found
	@# `--wait` (default) blocks until the namespace is fully gone. We intentionally
	@# wait so that a subsequent `make install` (or `make clean install`) doesn't
	@# race against finalizers — applying manifests into a still-terminating
	@# namespace errors out with "namespace X is being terminated".
	@echo "Deleting namespace $(namespace) and waiting for finalization..."
	-kubectl delete namespace $(namespace) --ignore-not-found --wait
