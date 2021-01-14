.PHONY: all
all: zeebe starter timer simpleStarter worker

.PHONY: zeebe
zeebe:
	-helm install zell-kw-02-benchmark zeebe/zeebe-cluster -f zeebe-values.yaml --skip-crds
	-kubectl apply -f curator-cronjob.yaml
	-kubectl apply -f curator-configmap.yaml

# Generates templates from the zeebe helm charts, useful to make some more specific changes which are not doable by the values file.
# To apply the templates use k apply -f zeebe-cluster/templates/
.PHONY: zeebe-template
zeebe-template:
	-helm template zell-kw-02-benchmark zeebe/zeebe-cluster -f zeebe-values.yaml --skip-crds --output-dir .

.PHONY: update
update:
	-helm upgrade zell-kw-02-benchmark zeebe/zeebe-cluster -f zeebe-values.yaml

.PHONY: starter
starter:
	kubectl apply -f starter.yaml

.PHONY: timer
timer:
	kubectl apply -f timer.yaml

.PHONY: simpleStarter
simpleStarter:
	kubectl apply -f simpleStarter.yaml

.PHONY: worker
worker:
	kubectl apply -f worker.yaml


.PHONY: clean
clean: clean-starter clean-timer clean-simpleStarter clean-worker clean-zeebe

.PHONY: clean-zeebe
clean-zeebe:
	-helm uninstall zell-kw-02-benchmark
	-kubectl delete -f curator-cronjob.yaml
	-kubectl delete -f curator-configmap.yaml
	-kubectl delete pvc -l app.kubernetes.io/instance=zell-kw-02-benchmark
	-kubectl delete pvc -l app=elasticsearch-master

.PHONY: clean-starter
clean-starter:
	-kubectl delete -f starter.yaml

.PHONY: clean-timer
clean-timer:
	-kubectl delete -f timer.yaml

.PHONY: clean-simpleStarter
clean-simpleStarter:
	-kubectl delete -f simpleStarter.yaml

.PHONY: clean-worker
clean-worker:
	-kubectl delete -f worker.yaml
