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

package integration

import (
	"fmt"
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"github.com/zeebe-io/zeebe/clients/go/zbc"
)

type PayloadType struct {
	Name string `json:"a"`
}

func (cmd PayloadType) String() string {
	return fmt.Sprintf("{\"a\": \"%s\"}", cmd.Name)
}

var _ = Describe("CreateInstance", func() {

	var client zbc.ZBClient
	BeforeEach(func() {
		c, e := zbc.NewZBClient("0.0.0.0:26500")
		Expect(e).To(BeNil())
		Expect(c).NotTo(BeNil())
		client = c
	})

	AfterEach(func() {
		client.Close()
	})

	Context("create instance", func() {

		It("deploy and create one workflow instance no payload", func() {
			response, err := client.NewDeployWorkflowCommand().AddResourceFile("../../../java/src/test/resources/workflows/demo-process.bpmn").Send()
			Expect(err).To(BeNil())

			Expect(len(response.GetWorkflows())).To(Equal(1))
			Expect(response.GetWorkflows()[0].BpmnProcessId).To(Equal("demoProcess"))
			Expect(response.GetWorkflows()[0].ResourceName).To(Equal("../../../java/src/test/resources/workflows/demo-process.bpmn"))

			createInstanceResponse, err := client.
				NewCreateInstanceCommand().
				BPMNProcessId("demoProcess").
				LatestVersion().
				Send()

			Expect(err).To(BeNil())
			Expect(createInstanceResponse.WorkflowInstanceKey).To(Not(Equal(0)))
		})

		It("deploy and create one workflow instance with payload from map", func() {
			response, err := client.NewDeployWorkflowCommand().AddResourceFile("../../../java/src/test/resources/workflows/demo-process.bpmn").Send()
			Expect(err).To(BeNil())

			Expect(len(response.GetWorkflows())).To(Equal(1))
			Expect(response.GetWorkflows()[0].BpmnProcessId).To(Equal("demoProcess"))
			Expect(response.GetWorkflows()[0].ResourceName).To(Equal("../../../java/src/test/resources/workflows/demo-process.bpmn"))

			payload := make(map[string]interface{})
			payload["name"] = "zeebe"

			createInstanceRequest, err := client.
				NewCreateInstanceCommand().
				BPMNProcessId("demoProcess").
				LatestVersion().
				PayloadFromMap(payload)
			Expect(err).To(BeNil())

			createInstanceResponse, err := createInstanceRequest.Send()
			Expect(err).To(BeNil())
			Expect(createInstanceResponse.WorkflowInstanceKey).To(Not(Equal(0)))
		})

		It("deploy and create one workflow instance with payload from object", func() {
			response, err := client.NewDeployWorkflowCommand().AddResourceFile("../../../java/src/test/resources/workflows/demo-process.bpmn").Send()
			Expect(err).To(BeNil())

			Expect(len(response.GetWorkflows())).To(Equal(1))
			Expect(response.GetWorkflows()[0].BpmnProcessId).To(Equal("demoProcess"))
			Expect(response.GetWorkflows()[0].ResourceName).To(Equal("../../../java/src/test/resources/workflows/demo-process.bpmn"))

			payload := PayloadType{Name: "bla"}

			createInstanceRequest, err := client.
				NewCreateInstanceCommand().
				BPMNProcessId("demoProcess").
				LatestVersion().
				PayloadFromObject(payload)
			Expect(err).To(BeNil())

			createInstanceResponse, err := createInstanceRequest.Send()
			Expect(err).To(BeNil())
			Expect(createInstanceResponse.WorkflowInstanceKey).To(Not(Equal(0)))
		})

		It("deploy and create one workflow instance with payload from string", func() {
			response, err := client.NewDeployWorkflowCommand().AddResourceFile("../../../java/src/test/resources/workflows/demo-process.bpmn").Send()
			Expect(err).To(BeNil())

			Expect(len(response.GetWorkflows())).To(Equal(1))
			Expect(response.GetWorkflows()[0].BpmnProcessId).To(Equal("demoProcess"))
			Expect(response.GetWorkflows()[0].ResourceName).To(Equal("../../../java/src/test/resources/workflows/demo-process.bpmn"))

			createInstanceRequest, err := client.
				NewCreateInstanceCommand().
				BPMNProcessId("demoProcess").
				LatestVersion().
				PayloadFromString("{\"name\": \"awesomeinstance\"}")
			Expect(err).To(BeNil())
			createInstanceResponse, err := createInstanceRequest.Send()
			Expect(err).To(BeNil())
			Expect(createInstanceResponse.WorkflowInstanceKey).To(Not(Equal(0)))
		})

		It("deploy and create one workflow instance with payload from stringer", func() {
			response, err := client.NewDeployWorkflowCommand().AddResourceFile("../../../java/src/test/resources/workflows/demo-process.bpmn").Send()
			Expect(err).To(BeNil())

			Expect(len(response.GetWorkflows())).To(Equal(1))
			Expect(response.GetWorkflows()[0].BpmnProcessId).To(Equal("demoProcess"))
			Expect(response.GetWorkflows()[0].ResourceName).To(Equal("../../../java/src/test/resources/workflows/demo-process.bpmn"))

			payload := PayloadType{Name: "bla"}

			createInstanceRequest, err := client.
				NewCreateInstanceCommand().
				BPMNProcessId("demoProcess").
				LatestVersion().
				PayloadFromStringer(payload)

			Expect(err).To(BeNil())
			createInstanceResponse, err := createInstanceRequest.Send()
			Expect(err).To(BeNil())
			Expect(createInstanceResponse.WorkflowInstanceKey).To(Not(Equal(0)))
		})

	})
})
