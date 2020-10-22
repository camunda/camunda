.PHONY: all
all: secret starter worker

.PHONY: secret
secret:
	kubectl apply -f cloudcredentials.yaml

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
clean: clean-secret clean-starter clean-timer clean-simpleStarter clean-worker

.PHONY: clean-secret
clean-secret:
	-kubectl delete -f cloudcredentials.yaml

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
