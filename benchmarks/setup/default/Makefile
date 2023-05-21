namespace ?= default

.PHONY: all
all: benchmark

.PHONY: benchmark
benchmark:
	helm install --namespace $(namespace) $(namespace) zeebe-benchmark/zeebe-benchmark -f values.yaml --skip-crds

# To deploy the Zeebe pods on stable nodes, use this job
.PHONY: benchmark-stable
benchmark:
	helm install --namespace $(namespace) $(namespace) zeebe-benchmark/zeebe-benchmark -f values.yaml -f values-stable.yaml --skip-crds

# Generates templates from the zeebe helm charts, useful to make some more specific changes which are not doable by the values file.
# To apply the templates use k apply -f zeebe-benchmark/templates/
.PHONY: template
template:
	helm template $(namespace) zeebe-benchmark/zeebe-benchmark -f values.yaml --skip-crds --output-dir .

.PHONY: template-stable
benchmark:
	helm template $(namespace) zeebe-benchmark/zeebe-benchmark -f values.yaml -f values-stable.yaml --skip-crds --output-dir .

.PHONY: update
update:
	helm upgrade --namespace $(namespace) $(namespace) zeebe-benchmark/zeebe-benchmark --reuse-values -f values.yaml

.PHONY: update-stable
update:
	helm upgrade --namespace $(namespace) $(namespace) zeebe-benchmark/zeebe-benchmark --reuse-values -f values.yaml -f values-stable.yaml

.PHONY: clean
clean:
	-helm --namespace $(namespace) uninstall $(namespace)
	-kubectl delete -n $(namespace) pvc -l app.kubernetes.io/instance=$(namespace)
	-kubectl delete -n $(namespace) pvc -l app=elasticsearch-master
