package utils

import "time"

const (
	LatestVersion                 = -1
	DefaultRetries                = 3
	RequestTimeoutInSec           = 5
	StreamTimeoutInSec            = 15
	DefaultJobTimeout             = time.Duration(5 * time.Minute)
	DefaultJobTimeoutInMs         = int64(DefaultJobTimeout / time.Millisecond)
	DefaultJobWorkerName          = "default"
	DefaultJobWorkerBufferSize    = 32
	DefaultJobWorkerConcurrency   = 4
	DefaultJobWorkerPollInterval  = 5 * time.Second
	DefaultJobWorkerPollThreshold = 0.3
)
