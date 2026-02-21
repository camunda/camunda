# Performance and Reliability Tests

> Back to [Testing Strategy](./README.md)

## Performance Tests

- Run **weekly** and before releases — never on PRs
- Use JMH for microbenchmarks (`microbenchmarks/` module)
- Use the load tester (`load-tests/load-tester/`) for system-level throughput tests
- Throughput SLOs: 50 PI/s (typical), 300 PI/s (stress) — see `docs/testing/reliability-testing.md`

## Reliability Tests

- Chaos engineering via `zbchaos` CLI
- Automated chaos experiments via Camunda BPMN processes
- Run weekly on dedicated GKE infrastructure
