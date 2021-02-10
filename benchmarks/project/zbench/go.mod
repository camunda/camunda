module github.com/zeebe-io/zeebe/benchmarks/project/zbench

go 1.15

require (
	github.com/grpc-ecosystem/go-grpc-prometheus v1.2.0
	github.com/prometheus/client_golang v1.9.0
	github.com/spf13/cobra v1.1.3
	github.com/zeebe-io/zeebe/clients/go v0.26.0
	google.golang.org/grpc v1.35.0
)

replace github.com/zeebe-io/zeebe/clients/go => ../../../clients/go
