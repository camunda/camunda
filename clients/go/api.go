package zbc

import "github.com/zeebe-io/zeebe/clients/go/pb"

type ZBClient interface {
	HealthCheck() (*pb.HealthResponse, error)
}