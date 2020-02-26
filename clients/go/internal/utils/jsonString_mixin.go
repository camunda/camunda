// Copyright © 2018 Camunda Services GmbH (info@camunda.com)
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package utils

import (
	"encoding/json"
	"fmt"
)

type SerializerMixin interface {
	Validate(string, string) error
	AsJSON(string, interface{}, bool) (string, error)
}

type JSONStringSerializer struct {
	valueMap map[string]interface{}
}

func (validator *JSONStringSerializer) Validate(name string, value string) error {
	err := json.Unmarshal([]byte(value), &validator.valueMap)
	if err != nil {
		return fmt.Errorf("parameter %q requires a JSON object, got %q: %s", name, value, err)
	}
	return nil
}

func (validator *JSONStringSerializer) AsJSON(name string, value interface{}, ignoreOmitempty bool) (string, error) {
	if ignoreOmitempty {
		value = MapMarshal(value, "json", false, true)
	}
	b, err := json.Marshal(value)
	if err != nil {
		return "", fmt.Errorf("parameter %q requires a JSON object, got %q: %s", name, value, err)
	}
	return string(b), nil
}

func NewJSONStringSerializer() SerializerMixin {
	return &JSONStringSerializer{
		valueMap: make(map[string]interface{}),
	}
}
