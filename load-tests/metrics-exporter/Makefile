DOCKER_PUSH ?= false
IMAGE ?= registry.camunda.cloud/team-reliability-testing/metrics-exporter:latest

.PHONY: build
build:
	go build

.PHONY: test
test:
	go test -race -count=1 ./...

.PHONY: lint
lint:
	go fmt ./...
	go vet ./...
	golangci-lint run --timeout 5m

.PHONY: docker
docker:
	docker buildx build --tag $(IMAGE) --push=$(DOCKER_PUSH) .
