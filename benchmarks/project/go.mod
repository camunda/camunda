module github.com/zeebe-io/zeebe/benchmarks/project

go 1.13

require (
	github.com/prometheus/client_golang v0.9.3
	github.com/spf13/cobra v1.0.0
	github.com/zeebe-io/zeebe/clients/go v0.0.0-20200610171138-fca862466987
)

replace github.com/zeebe-io/zeebe/clients/go => ../../clients/go
