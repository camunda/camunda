SRC = load-test-tool

all: fmt

.PHONY: fmt
fmt:
	uv run ruff format $(SRC)
	uv run ruff check --fix $(SRC)

.PHONY: check
check: fmt
	uv run ty check $(SRC)

.PHONY: test
test:
	uv run pytest

.PHONY: ty
ty:
	uv run ty check
