namespace ?= default

.PHONY: all
all: secret starter worker

.PHONY: secret
secret:
	kubectl apply -n $(namespace) -f cloudcredentials.yaml

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
clean: clean-secret clean-starter clean-timer clean-simpleStarter clean-worker

.PHONY: clean-secret
clean-secret:
	-kubectl delete -n $(namespace) -f cloudcredentials.yaml

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
