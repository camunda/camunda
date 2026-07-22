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
# - physical_tenants_rdbms_values_file
#   Set only for versions that support the two-physical-tenants RDBMS values
#   file. Leave empty otherwise so physical_tenants=true fails fast.

rdbms_storages ?= postgresql mysql mariadb mssql oracle
optimize_self_sufficient_storages ?= elasticsearch opensearch
scenario_max_override_key ?= orchestration.extraConfiguration[1].content=
install_storage_target ?= install-storage
physical_tenants_rdbms_values_file ?= camunda-platform-two-physical-tenants-shared-rdbms.yaml

template_output_dir ?= .
# Enable the chaos-killer CronJob (randomly deletes one matching pod per run).
# Use named targets (make install-chaos) or pass directly: make install chaos=true
chaos ?= false
# Deploy a second physical tenant (testfoo) alongside the default one, sharing the
# same RDBMS but isolated by table prefix. Requires an rdbms secondary_storage.
# Pass directly: make install physical_tenants=true secondary_storage=postgresql
physical_tenants ?= false
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

# Physical tenants are isolated by RDBMS table prefix, so they only make sense with
# an rdbms secondary storage. Fail fast on any other combination.
# (Nested conditionals are left-aligned: a leading tab would make `$(error ...)` look like a recipe.)
ifeq ($(physical_tenants),true)
ifeq ($(physical_tenants_rdbms_values_file),)
$(error physical_tenants=true is not supported for this Camunda version (no physical_tenants_rdbms_values_file declared))
endif
ifeq ($(filter $(secondary_storage),$(rdbms_storages)),)
$(error physical_tenants=true requires an rdbms secondary_storage ($(rdbms_storages)), got '$(secondary_storage)')
endif
endif

# Add secondary storage values
ifneq ($(filter $(secondary_storage),$(rdbms_storages)),)
	ifeq ($(physical_tenants),true)
		# Same as camunda-platform-values-rdbms.yaml, plus a second physical tenant (testfoo).
		platform_values += -f $(physical_tenants_rdbms_values_file)
	else
		platform_values += -f camunda-platform-values-rdbms.yaml
	endif
else ifeq ($(secondary_storage),none)
	_load_test_setup_flags += --set global.extraConfig.load-tester.monitor-data-availability=false
endif
platform_values += -f camunda-platform-values-$(secondary_storage).yaml

install_es_prom_exporter = false

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

        install_es_prom_exporter = true
        es_prom_exporter_es_uri = http://opensearch:9200
        _load_test_setup_flags += --set metricsExporter.database.url=http://opensearch:9200
    else ifeq ($(secondary_storage),elasticsearch)
        # When deploying the Elasticsearch secondary storage, Optimize needs the
        # Elasticsearch-specific exporter/client configuration.
        platform_values += -f camunda-platform-values-optimize-elasticsearch.yaml

        install_es_prom_exporter = true
        es_prom_exporter_es_uri = http://elasticsearch-es-http:9200
        _load_test_setup_flags += --set metricsExporter.database.url=http://elasticsearch-es-http:9200
    else
        ifeq ($(filter $(secondary_storage),$(optimize_self_sufficient_storages)),)
            # If we are not using Elasticsearch/OpenSearch as a secondary storage, Optimize will
            # be configured with the Elasticsearch backend, either:
            # * Using the same Elasticsearch cluster as the secondary storage, if
            #   the secondary storage is also Elasticsearch.
            # * Or using its dedicated Elasticsearch cluster, different from the
            #   configured secondary storage.
            platform_values += -f camunda-platform-values-optimize-elasticsearch.yaml
            install_es_prom_exporter = true
            es_prom_exporter_es_uri = http://elasticsearch-es-http:9200
            _load_test_setup_flags += --set metricsExporter.database.url=http://elasticsearch-es-http:9200
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

# When physical_tenants=true, also deploy the second (testfoo) load tester. Appended as the
# last prerequisite so it runs after the platform is up and the load-test-credentials secret exists.
# Only main defines install-load-test-physical-tenant; harmless no-op declaration everywhere
# else since physical_tenants can never be true there (physical_tenants_rdbms_values_file is empty).
ifeq ($(physical_tenants),true)
install: install-load-test-physical-tenant
install-stable: install-load-test-physical-tenant
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

# Apply the camunda-credentials secret. Idempotent: rerunning after a TTL
# deletion reapplies the SAME credentials, so the orchestration secret in
# load-tester-values-defaults.yaml stays in sync with the live secret.
.PHONY: create-credentials
create-credentials:
	@echo "Applying camunda-credentials secret for namespace $(namespace)..."
	kubectl apply -n $(namespace) -f resources/camunda-credentials.yaml

ifneq ($(install_storage_target),)
# Install secondary storage based on configuration
.PHONY: install-storage
install-storage:
ifeq ($(secondary_storage),postgresql)
	@echo "Installing PostgreSQL database for namespace $(namespace)..."
	# Install Postgres database - configuration provided via camunda-platform-values-defaults.yaml, camunda-platform-values-rdbms.yaml and camunda-platform-values-postgresql.yaml
	helm upgrade --install postgresql oci://registry-1.docker.io/bitnamicharts/postgresql \
		--namespace $(namespace) \
		$(platform_values)
else ifeq ($(secondary_storage),mysql)
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
else ifeq ($(secondary_storage),opensearch)
	@echo "Installing OpenSearch cluster for namespace $(namespace)..."
	helm upgrade --install opensearch opensearch/opensearch \
		--version "2.31.0" \
		--namespace $(namespace) \
		$(platform_values)
else
	@echo "Skipping secondary storage installation (secondary_storage=$(secondary_storage))"
endif
endif

ifneq ($(physical_tenants_rdbms_values_file),)
# Deploy a second load tester that drives the `testfoo` physical tenant.
# The camunda-load-tests subchart hardcodes the starter/worker resource names, so a second
# Helm release would collide. Instead we render only those two templates from the same chart,
# values, scenario and image as the default tester, rename them to *-testfoo, and apply.
# REST is required because gRPC only routes to the default physical tenant.
.PHONY: install-load-test-physical-tenant
install-load-test-physical-tenant:
	@echo "Deploying the testfoo physical-tenant load tester for namespace $(namespace)..."
	@# Clone the generated load-test-credentials secret, overriding only the REST address to the
	@# testfoo tenant path. Inherits every real credential value (clientId/secret, authServer, audience).
	kubectl get secret load-test-credentials -n $(namespace) -o json \
	  | jq '.data.zeebeRestAddress = ("http://camunda:8080/physical-tenants/testfoo" | @base64) | .metadata.name = "load-test-credentials-testfoo" | del(.metadata.uid,.metadata.resourceVersion,.metadata.creationTimestamp,.metadata.ownerReferences,.metadata.managedFields)' \
	  | kubectl apply -n $(namespace) -f -
	@# Render only the subchart starter+worker, rename to *-testfoo, and apply.
	helm template load-test-setup $(helm_chart_load_test_setup) \
	    --namespace $(namespace) \
	    -s charts/load-tester/templates/starter.yaml \
	    -s charts/load-tester/templates/workers.yaml \
	    $(load_test_setup_flags) \
	    --set load-tester.enabled=true \
	    --set global.preferRest.enabled=true \
	    --set load-tester.saas.credentials.existingSecret=load-test-credentials-testfoo \
	  | sed -E 's/: starter$$/: starter-testfoo/; s/: worker$$/: worker-testfoo/' \
	  | kubectl apply -n $(namespace) -f -
endif

# Install/upgrade Camunda Platform helm chart
.PHONY: install-platform
install-platform:
	helm upgrade $(namespace) $(helm_chart_platform) \
		--install \
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
	@echo "Deleting namespace $(namespace) and waiting for finalization..."
	@# `--wait` (default) blocks until the namespace is fully gone. We intentionally
	@# wait so that a subsequent `make install` (or `make clean install`) doesn't
	@# race against finalizers — applying manifests into a still-terminating
	@# namespace errors out with "namespace X is being terminated".
	-kubectl delete namespace $(namespace) --ignore-not-found --wait
