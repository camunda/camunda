# Build and push the images used by the Helm chart.
registry ?= registry.camunda.cloud/team-zeebe
tag ?= 7.24.0-SNAPSHOT
platform ?= linux/amd64

.PHONY: build
build:
	docker buildx build --platform $(platform) -f engine/Dockerfile -t $(registry)/c7-benchmark-engine:$(tag) --load .
	docker buildx build --platform $(platform) -f load-tester/Dockerfile -t $(registry)/c7-benchmark-load-tester:$(tag) --load .
	docker buildx build --platform $(platform) -t $(registry)/c7-benchmark-results:latest --load results

.PHONY: push
push: build
	docker push $(registry)/c7-benchmark-engine:$(tag)
	docker push $(registry)/c7-benchmark-load-tester:$(tag)
	docker push $(registry)/c7-benchmark-results:latest

.PHONY: dashboard
dashboard:
	python3 monitor/grafana/generate_dashboard.py

# Local stack (see local/docker-compose.yml)
.PHONY: local-up local-down local-reset
local-up:
	cd local && docker compose up -d --build
local-down:
	cd local && docker compose down
local-reset:
	cd local && docker compose down -v

# Results folder for the local stack: make results DURATION=1800 RUN_ID=my-run
DURATION ?= 1800
RUN_ID ?=
.PHONY: results
results:
	docker build -q -t c7-bench/results results >/dev/null
	mkdir -p results-out
	docker run --rm --network c7-benchmark_default -e PROMETHEUS_URL=http://prometheus:9090 \
		-e METRICS_NAMESPACE=local -e DURATION=$(DURATION) -e RUN_ID=$(RUN_ID) \
		-e START=$(START) -e END=$(END) -v "$(CURDIR)/results-out":/results c7-bench/results
