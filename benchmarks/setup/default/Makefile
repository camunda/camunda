.PHONY: all
all: zeebe starter timer simpleStarter worker

.PHONY: zeebe
zeebe:
	-helm install --namespace default default zeebe/zeebe-cluster -f zeebe-values.yaml --skip-crds
	-kubectl apply -n default -f curator-cronjob.yaml
	-kubectl apply -n default -f curator-configmap.yaml

# Generates templates from the zeebe helm charts, useful to make some more specific changes which are not doable by the values file.
# To apply the templates use k apply -f zeebe-cluster/templates/
.PHONY: zeebe-template
zeebe-template:
	-helm template default zeebe/zeebe-cluster -f zeebe-values.yaml --skip-crds --output-dir .

.PHONY: update
update:
	-helm upgrade --namespace default default zeebe/zeebe-cluster -f zeebe-values.yaml

.PHONY: starter
starter:
	kubectl apply -n default -f starter.yaml

.PHONY: timer
timer:
	kubectl apply -n default -f timer.yaml

.PHONY: simpleStarter
simpleStarter:
	kubectl apply -n default -f simpleStarter.yaml

.PHONY: worker
worker:
	kubectl apply -n default -f worker.yaml


.PHONY: clean
clean: clean-starter clean-timer clean-simpleStarter clean-worker clean-zeebe

.PHONY: clean-zeebe
clean-zeebe:
	-helm --namespace default uninstall default
	-kubectl delete -n default -f curator-cronjob.yaml
	-kubectl delete -n default -f curator-configmap.yaml
	-kubectl delete -n default pvc -l app.kubernetes.io/instance=default
	-kubectl delete -n default pvc -l app=elasticsearch-master

.PHONY: clean-starter
clean-starter:
	-kubectl delete -n default -f starter.yaml

.PHONY: clean-timer
clean-timer:
	-kubectl delete -n default -f timer.yaml

.PHONY: clean-simpleStarter
clean-simpleStarter:
	-kubectl delete -n default -f simpleStarter.yaml

.PHONY: clean-worker
clean-worker:
	-kubectl delete -n default -f worker.yaml
