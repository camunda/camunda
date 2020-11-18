module github.com/zeebe-io/zeebe/benchmarks/project/zbench

go 1.13

require (
	github.com/grpc-ecosystem/go-grpc-prometheus v1.2.0
	github.com/prometheus/client_golang v1.8.0
	github.com/spf13/cobra v1.1.1
	github.com/zeebe-io/zeebe/clients/go v0.25.1
	google.golang.org/grpc v1.33.2
)

replace github.com/zeebe-io/zeebe/clients/go => ../../../clients/go
