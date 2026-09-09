UV ?= uv
PYTEST_ARGS ?= --verbose --color=yes --tb=short

.PHONY: test compile format lint check

test:
	$(UV) run --locked pytest $(PYTEST_ARGS) tests

compile:
	$(UV) run --locked python -m py_compile \
		cli.py \
		errors.py \
		load_test_report.py \
		prometheus.py \
		queries.py \
		report.py

format:
	$(UV) run --locked ruff check --fix .
	$(UV) run --locked ruff format .

lint:
	$(UV) run --locked ruff check .
	$(UV) run --locked ruff format --check .

check: compile lint test
