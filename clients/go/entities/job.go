package entities

import (
	"encoding/json"
	"github.com/zeebe-io/zeebe/clients/go/pb"
)

type Job struct {
	pb.ActivatedJob
}

func (job *Job) GetPayloadAsMap() (map[string]interface{}, error) {
	var payloadMap map[string]interface{}
	return payloadMap, job.GetPayloadAs(&payloadMap)
}

func (job *Job) GetPayloadAs(payloadType interface{}) error {
	return json.Unmarshal([]byte(job.Payload), payloadType)
}

func (job *Job) GetCustomHeadersAsMap() (map[string]interface{}, error) {
	var customHeadersMap map[string]interface{}
	return customHeadersMap, job.GetCustomHeadersAs(&customHeadersMap)
}

func (job *Job) GetCustomHeadersAs(customHeadersType interface{}) error {
	return json.Unmarshal([]byte(job.CustomHeaders), customHeadersType)
}
