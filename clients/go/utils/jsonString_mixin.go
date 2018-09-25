package utils

import (
	"encoding/json"
)

type SerializerMixin interface {
	Validate([]byte) bool
	ToString(interface{}) (string, error)
}

type JsonStringSerializer struct {
	payloadMap map[string]interface{}
}

func (validator *JsonStringSerializer) Validate(payload []byte) bool {
	return json.Unmarshal(payload, &validator.payloadMap) == nil
}

func (validator *JsonStringSerializer) ToString(payload interface{}) (string, error) {
	b, err := json.Marshal(payload)
	if err != nil {
		return "", err
	}
	return string(b), nil
}

func NewJsonStringSerializer() SerializerMixin {
	return &JsonStringSerializer{
		payloadMap: make(map[string]interface{}),
	}
}
