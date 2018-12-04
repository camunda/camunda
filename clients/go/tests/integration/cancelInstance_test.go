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
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"github.com/zeebe-io/zeebe/clients/go/zbc"
)

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

	Context("cancel instance", func() {

		It("should deploy, create and cancel workflow instance", func() {
			_, err := client.NewDeployWorkflowCommand().AddResourceFile("../../../java/src/test/resources/workflows/demo-process.bpmn").Send()
			Expect(err).To(BeNil())

			createInstanceResponse, err := client.
				NewCreateInstanceCommand().
				BPMNProcessId("demoProcess").
				LatestVersion().
				Send()

			Expect(err).To(BeNil())
			Expect(createInstanceResponse.WorkflowInstanceKey).To(Not(Equal(0)))

			_, err = client.
				NewCancelInstanceCommand().
				WorkflowInstanceKey(createInstanceResponse.WorkflowInstanceKey).
				Send()
			Expect(err).To(BeNil())

		})

	})
})
