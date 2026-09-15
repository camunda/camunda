UV ?= uv
PYTEST_ARGS ?= --verbose --color=yes --tb=short

.PHONY: test format lint check

test:
	$(UV) run pytest $(PYTEST_ARGS) tests

format:
	$(UV) run ruff check --fix .
	$(UV) run ruff format .

lint:
	$(UV) run ruff check .
	$(UV) run ruff format --check .

check: lint test
