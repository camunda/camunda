package integration

import (
	. "github.com/onsi/ginkgo"
	. "github.com/onsi/gomega"
	"github.com/zeebe-io/zeebe/clients/go"
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
