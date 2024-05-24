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
	"testing"

	"github.com/camunda/zeebe/clients/go/v8/pkg/pb"
	"github.com/google/go-cmp/cmp"
)

type testType struct {
	Foo   string
	Hello string
}

var (
	job = Job{&pb.ActivatedJob{
		CustomHeaders: `{"foo": "bar", "hello": "world"}`,
		Variables:     `{"foo": "bar", "hello": "world"}`,
	}}
	wantStruct = testType{
		Foo:   "bar",
		Hello: "world",
	}
)

func TestJob_GetVariablesAsMap(t *testing.T) {
	got, err := job.GetVariablesAsMap()
	if err != nil {
		t.Fatalf("job.GetVariablesAsMap() = %v", err)
	}

	want := map[string]interface{}{
		"foo":   "bar",
		"hello": "world",
	}

	if diff := cmp.Diff(want, got); diff != "" {
		t.Errorf("job.GetVariablesAsMap() differs (-want +got):\n%s", diff)
	}
}

func TestJob_GetVariablesAs(t *testing.T) {
	var got testType

	if err := job.GetVariablesAs(&got); err != nil {
		t.Fatalf("job.GetVariablesAs(&%T) = %v", got, err)
	}

	if diff := cmp.Diff(wantStruct, got); diff != "" {
		t.Errorf("job.GetVariablesAs(%T) differs (-want +got):\n%s", got, diff)
	}
}

func TestJob_GetCustomHeadersAsMap(t *testing.T) {
	got, err := job.GetCustomHeadersAsMap()
	if err != nil {
		t.Fatalf("job.GetCustomHeadersAsMap() = %v", err)
	}

	want := map[string]string{
		"foo":   "bar",
		"hello": "world",
	}

	if diff := cmp.Diff(want, got); diff != "" {
		t.Errorf("job.GetCustomHeadersAsMap() differs (-want +got):\n%s", diff)
	}
}

func TestJob_GetCustomHeadersAs(t *testing.T) {
	var got testType

	if err := job.GetCustomHeadersAs(&got); err != nil {
		t.Fatalf("job.GetCustomHeadersAs(&%T) = %v", got, err)
	}

	if diff := cmp.Diff(wantStruct, got); diff != "" {
		t.Errorf("job.GetCustomHeadersAs(%T) differs (-want +got):\n%s", got, diff)
	}
}
