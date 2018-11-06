package utils

import (
	"encoding/json"
	"fmt"
)

type SerializerMixin interface {
	Validate(string, string) error
	AsJson(string, interface{}) (string, error)
}

type JsonStringSerializer struct {
	valueMap map[string]interface{}
}

func (validator *JsonStringSerializer) Validate(name string, value string) error {
	err := json.Unmarshal([]byte(value), &validator.valueMap)
	if err != nil {
		return fmt.Errorf("parameter %q requires a JSON object, got %q: %s", name, value, err)
	}
	return nil
}

func (validator *JsonStringSerializer) AsJson(name string, value interface{}) (string, error) {
	b, err := json.Marshal(value)
	if err != nil {
		return "", fmt.Errorf("parameter %q requires a JSON object, got %q: %s", name, value, err)
	}
	return string(b), nil
}

func NewJsonStringSerializer() SerializerMixin {
	return &JsonStringSerializer{
		valueMap: make(map[string]interface{}),
	}
}
