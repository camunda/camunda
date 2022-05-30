namespace ?= medic-cw-22-a267605362-benchmark

.PHONY: all
all: zeebe starter timer simpleStarter worker

.PHONY: zeebe
zeebe:
	helm install --namespace $(namespace) $(namespace) camunda/camunda-platform -f zeebe-values.yaml --skip-crds

# Generates templates from the zeebe helm charts, useful to make some more specific changes which are not doable by the values file.
# To apply the templates use k apply -f camunda-platform/templates/
.PHONY: template
template:
	helm template $(namespace) camunda/camunda-platform -f zeebe-values.yaml --skip-crds --output-dir .

.PHONY: update
update:
	helm upgrade --namespace $(namespace) $(namespace) camunda/camunda-platform -f zeebe-values.yaml

.PHONY: starter
starter:
	kubectl apply -n $(namespace) -f starter.yaml

.PHONY: timer
timer:
	kubectl apply -n $(namespace) -f timer.yaml

.PHONY: simpleStarter
simpleStarter:
	kubectl apply -n $(namespace) -f simpleStarter.yaml

.PHONY: worker
worker:
	kubectl apply -n $(namespace) -f worker.yaml


.PHONY: clean
clean: clean-starter clean-timer clean-simpleStarter clean-worker clean-zeebe

.PHONY: clean-zeebe
clean-zeebe:
	-helm --namespace $(namespace) uninstall $(namespace)
	-kubectl delete -n $(namespace) pvc -l app.kubernetes.io/instance=$(namespace)
	-kubectl delete -n $(namespace) pvc -l app=elasticsearch-master

.PHONY: clean-starter
clean-starter:
	-kubectl delete -n $(namespace) -f starter.yaml

.PHONY: clean-timer
clean-timer:
	-kubectl delete -n $(namespace) -f timer.yaml

.PHONY: clean-simpleStarter
clean-simpleStarter:
	-kubectl delete -n $(namespace) -f simpleStarter.yaml

.PHONY: clean-worker
clean-worker:
	-kubectl delete -n $(namespace) -f worker.yaml
