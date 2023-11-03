module github.com/zeebe-io/zeebe/benchmarks/project/zbench

go 1.15

require (
	github.com/camunda/zeebe/clients/go/v8 v8.3.1 // indirect
	github.com/go-kit/kit v0.10.0 // indirect
	github.com/grpc-ecosystem/go-grpc-prometheus v1.2.0
	github.com/prometheus/client_golang v1.14.0
	github.com/spf13/cobra v1.7.0
	github.com/zeebe-io/zeebe/clients/go v0.26.6
	google.golang.org/grpc v1.58.3
)

replace github.com/zeebe-io/zeebe/clients/go => ../../../clients/go
