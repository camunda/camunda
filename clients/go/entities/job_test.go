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

package entities

import (
	"github.com/zeebe-io/zeebe/clients/go/pb"
	"reflect"
	"testing"
)

type MyType struct {
	Foo   string
	Hello string
}

var (
	expectedJson      = "{\"foo\": \"bar\", \"hello\": \"world\"}"
	expectedJsonAsMap = map[string]string{
		"foo":   "bar",
		"hello": "world",
	}
	expectedJsonAsStruct = MyType{
		Foo:   "bar",
		Hello: "world",
	}
	job = Job{pb.ActivatedJob{
		CustomHeaders: expectedJson,
		Variables:     expectedJson,
	}}
)

func TestJob_GetVariablesAsMap(t *testing.T) {
	variables, err := job.GetVariablesAsMap()
	if err != nil {
		t.Error("Failed to get variables as map", err)
	}
	if reflect.DeepEqual(variables, expectedJsonAsMap) {
		t.Error("Failed to get variables as map, got", variables, "instead of", expectedJsonAsMap)
	}
}

func TestJob_GetVariablesAs(t *testing.T) {
	var variables MyType
	if err := job.GetVariablesAs(&variables); err != nil {
		t.Error("Failed to get variables as struct", err)
	}
	if variables != expectedJsonAsStruct {
		t.Error("Failed to get variables as struct, got", variables, "instead of", expectedJsonAsStruct)
	}
}

func TestJob_GetCustomHeadersAsMap(t *testing.T) {
	customHeaders, err := job.GetCustomHeadersAsMap()
	if err != nil {
		t.Error("Failed to get custom headers as map", err)
	}
	if reflect.DeepEqual(customHeaders, expectedJsonAsMap) {
		t.Error("Failed to get custom headers as map, got", customHeaders, "instead of", expectedJsonAsMap)
	}
}

func TestJob_GetCustomHeadersAs(t *testing.T) {
	var customHeaders MyType
	if err := job.GetCustomHeadersAs(&customHeaders); err != nil {
		t.Error("Failed to get custom headers as struct", err)
	}
	if customHeaders != expectedJsonAsStruct {
		t.Error("Failed to get custom headers as struct, got", customHeaders, "instead of", expectedJsonAsStruct)
	}
}
