DOCKER_PUSH ?= false
IMAGE ?= registry.camunda.cloud/team-reliability-testing/metrics-exporter:latest

.PHONY: build
build:
	go build

.PHONY: test
test:
	go test -race -count=1 -cover ./...

.PHONY: lint
lint:
	go fmt ./...
	go vet ./...
	golangci-lint run --timeout 5m

.PHONY: integration-test
integration-test:
	go test -race -count=1 -cover -tags integration -timeout 10m ./...

.PHONY: docker
docker:
	docker buildx build --tag $(IMAGE) --push=$(DOCKER_PUSH) .
