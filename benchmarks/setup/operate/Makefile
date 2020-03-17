.PHONY: all
all: zeebe starter worker

.PHONY: zeebe
zeebe:
	-helm install operate zeebe/zeebe-full -f zeebe-values.yaml --skip-crds
	-kubectl apply -f curator-cronjob.yaml
	-kubectl apply -f curator-configmap.yaml

.PHONY: starter
starter:
	kubectl apply -f starter.yaml

.PHONY: worker
worker:
	kubectl apply -f worker.yaml


.PHONY: clean
clean: clean-starter clean-worker clean-zeebe

.PHONY: clean-zeebe
clean-zeebe:
	-helm uninstall operate
	-kubectl delete -f curator-cronjob.yaml
	-kubectl delete -f curator-configmap.yaml
	-kubectl delete pvc -l app=operate-zeebe
	-kubectl delete pvc -l app=elasticsearch-master

.PHONY: clean-starter
clean-starter:
	-kubectl delete -f starter.yaml

.PHONY: clean-worker
clean-worker:
	-kubectl delete -f worker.yaml
